import { Router, type IRouter, type NextFunction, type Request, type Response } from "express";
import {
  AdminLoginBody,
  AdminLoginResponse,
  ApproveAdminUserBody,
  ApproveAdminUserParams,
  ApproveAdminUserResponse,
  DeclineAdminUserParams,
  DeclineAdminUserResponse,
  GetAdminSessionResponse,
  GetAdminSummaryResponse,
  ListAdminUsersQueryParams,
  ListAdminUsersResponse,
} from "@workspace/api-zod";
import {
  approveProfile,
  declineProfile,
  getProfile,
  getSupabaseUser,
  listProfiles,
  refreshSupabaseSession,
  signInWithPassword,
  type SupabaseProfile,
  SupabaseHttpError,
} from "../lib/supabase-admin";
import {
  clearAdminSession,
  readAdminSession,
  writeAdminSession,
  type AdminSession,
} from "../lib/admin-session";

type AdminContext = {
  accessToken: string;
  profile: SupabaseProfile;
};

const router: IRouter = Router();

function toAdminUser(profile: SupabaseProfile) {
  return {
    id: profile.id,
    email: profile.email,
    approvalStatus: profile.approval_status,
    subscriptionPlan: profile.subscription_plan,
    subscriptionExpiresAt: profile.subscription_expires_at,
    isAdmin: profile.is_admin,
    createdAt: profile.created_at,
  };
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Unexpected server error.";
}

async function authenticateSession(
  session: AdminSession,
): Promise<{ context: AdminContext; session: AdminSession }> {
  let activeSession = session;
  let user: { id: string };

  try {
    user = await getSupabaseUser(activeSession.accessToken);
  } catch (error) {
    if (
      !(error instanceof SupabaseHttpError) ||
      error.status !== 401 ||
      !activeSession.refreshToken
    ) {
      throw error;
    }
    const refreshed = await refreshSupabaseSession(activeSession.refreshToken);
    activeSession = {
      accessToken: refreshed.access_token,
      refreshToken: refreshed.refresh_token ?? activeSession.refreshToken,
    };
    user = await getSupabaseUser(activeSession.accessToken);
  }

  const profile = await getProfile(activeSession.accessToken, user.id);
  if (!profile?.is_admin) {
    throw new SupabaseHttpError("Administrator access is required.", 403);
  }

  return {
    context: { accessToken: activeSession.accessToken, profile },
    session: activeSession,
  };
}

async function requireAdmin(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  const session = readAdminSession(req);
  if (!session) {
    res.status(401).json({ error: "Please sign in as an administrator." });
    return;
  }

  try {
    const authenticated = await authenticateSession(session);
    if (
      authenticated.session.accessToken !== session.accessToken ||
      authenticated.session.refreshToken !== session.refreshToken
    ) {
      writeAdminSession(res, authenticated.session);
    }
    res.locals.admin = authenticated.context;
    next();
  } catch (error) {
    if (error instanceof SupabaseHttpError && error.status === 403) {
      clearAdminSession(res);
      res.status(403).json({ error: error.message });
      return;
    }
    req.log.warn({ err: error }, "Admin session could not be validated");
    clearAdminSession(res);
    res.status(401).json({ error: "Your session has expired. Please sign in again." });
  }
}

router.post("/admin/login", async (req, res): Promise<void> => {
  const parsed = AdminLoginBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }

  try {
    const auth = await signInWithPassword(parsed.data.email, parsed.data.password);
    const userId = auth.user?.id;
    if (!userId) {
      res.status(400).json({ error: "Supabase did not return a user account." });
      return;
    }
    const profile = await getProfile(auth.access_token, userId);
    if (!profile?.is_admin) {
      res.status(403).json({ error: "This account is not an administrator." });
      return;
    }

    writeAdminSession(res, {
      accessToken: auth.access_token,
      refreshToken: auth.refresh_token ?? null,
    });
    res.json(
      AdminLoginResponse.parse({
        admin: toAdminUser(profile),
      }),
    );
  } catch (error) {
    const status = error instanceof SupabaseHttpError && error.status === 400 ? 400 : 401;
    req.log.warn({ err: error }, "Admin sign-in failed");
    res.status(status).json({ error: errorMessage(error) });
  }
});

router.post("/admin/logout", (_req, res): void => {
  clearAdminSession(res);
  res.sendStatus(204);
});

router.get("/admin/session", requireAdmin, (req, res): void => {
  const admin = res.locals.admin as AdminContext;
  res.json(GetAdminSessionResponse.parse({ admin: toAdminUser(admin.profile) }));
});

router.get("/admin/summary", requireAdmin, async (req, res): Promise<void> => {
  const admin = res.locals.admin as AdminContext;
  try {
    const profiles = await listProfiles(admin.accessToken);
    const summary = profiles.reduce(
      (counts, profile) => {
        counts.total += 1;
        counts[profile.approval_status.toLowerCase() as "pending" | "approved" | "declined"] += 1;
        return counts;
      },
      { total: 0, pending: 0, approved: 0, declined: 0 },
    );
    res.json(GetAdminSummaryResponse.parse(summary));
  } catch (error) {
    req.log.error({ err: error }, "Unable to load admin summary");
    res.status(502).json({ error: errorMessage(error) });
  }
});

router.get("/admin/users", requireAdmin, async (req, res): Promise<void> => {
  const parsed = ListAdminUsersQueryParams.safeParse(req.query);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const admin = res.locals.admin as AdminContext;

  try {
    const profiles = await listProfiles(admin.accessToken);
    const filtered =
      parsed.data.status === "ALL"
        ? profiles
        : profiles.filter((profile) => profile.approval_status === parsed.data.status);
    res.json(ListAdminUsersResponse.parse(filtered.map(toAdminUser)));
  } catch (error) {
    req.log.error({ err: error }, "Unable to load admin users");
    res.status(502).json({ error: errorMessage(error) });
  }
});

router.post(
  "/admin/users/:userId/subscription",
  requireAdmin,
  async (req, res): Promise<void> => {
    const params = ApproveAdminUserParams.safeParse(req.params);
    const body = ApproveAdminUserBody.safeParse(req.body);
    if (!params.success || !body.success) {
      res.status(400).json({
        error: !params.success ? params.error.message : body.error.message,
      });
      return;
    }
    const admin = res.locals.admin as AdminContext;

    try {
      const updated =
        (await approveProfile(admin.accessToken, params.data.userId, body.data.plan)) ??
        (await getProfile(admin.accessToken, params.data.userId));
      if (!updated) {
        res.status(404).json({ error: "User account not found." });
        return;
      }
      res.json(ApproveAdminUserResponse.parse(toAdminUser(updated)));
    } catch (error) {
      req.log.error({ err: error }, "Unable to approve user");
      res.status(error instanceof SupabaseHttpError && error.status === 403 ? 403 : 502).json({
        error: errorMessage(error),
      });
    }
  },
);

router.post(
  "/admin/users/:userId/decline",
  requireAdmin,
  async (req, res): Promise<void> => {
    const params = DeclineAdminUserParams.safeParse(req.params);
    if (!params.success) {
      res.status(400).json({ error: params.error.message });
      return;
    }
    const admin = res.locals.admin as AdminContext;

    try {
      const updated =
        (await declineProfile(admin.accessToken, params.data.userId)) ??
        (await getProfile(admin.accessToken, params.data.userId));
      if (!updated) {
        res.status(404).json({ error: "User account not found." });
        return;
      }
      res.json(DeclineAdminUserResponse.parse(toAdminUser(updated)));
    } catch (error) {
      req.log.error({ err: error }, "Unable to decline user");
      res.status(error instanceof SupabaseHttpError && error.status === 403 ? 403 : 502).json({
        error: errorMessage(error),
      });
    }
  },
);

export default router;