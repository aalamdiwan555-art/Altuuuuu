import { ReplitConnectors } from "@replit/connectors-sdk";

export type SupabaseAuthSession = {
  access_token: string;
  refresh_token?: string;
  user?: { id?: string; email?: string | null };
};

export type SupabaseProfile = {
  id: string;
  email: string;
  approval_status: "PENDING" | "APPROVED" | "DECLINED";
  subscription_plan: "NONE" | "ONE_DAY" | "TWO_DAYS" | "THREE_DAYS" | "LIFETIME";
  subscription_expires_at: string | null;
  is_admin: boolean;
  created_at: string;
};

export class SupabaseHttpError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

async function supabaseRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const connectors = new ReplitConnectors();
  const response = await connectors.proxy("supabase", path, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });
  const raw = await response.text();
  let data: unknown = null;
  if (raw) {
    try {
      data = JSON.parse(raw);
    } catch {
      data = raw;
    }
  }

  if (!response.ok) {
    const body = data as { message?: string; error_description?: string } | null;
    throw new SupabaseHttpError(
      body?.message ??
        body?.error_description ??
        `Supabase request failed with status ${response.status}.`,
      response.status,
    );
  }

  return data as T;
}

export async function signInWithPassword(
  email: string,
  password: string,
): Promise<SupabaseAuthSession> {
  return supabaseRequest<SupabaseAuthSession>(
    "/auth/v1/token?grant_type=password",
    {
      method: "POST",
      body: JSON.stringify({ email, password }),
    },
  );
}

export async function refreshSupabaseSession(
  refreshToken: string,
): Promise<SupabaseAuthSession> {
  return supabaseRequest<SupabaseAuthSession>(
    "/auth/v1/token?grant_type=refresh_token",
    {
      method: "POST",
      body: JSON.stringify({ refresh_token: refreshToken }),
    },
  );
}

export async function getSupabaseUser(
  accessToken: string,
): Promise<{ id: string; email?: string | null }> {
  return supabaseRequest<{ id: string; email?: string | null }>("/auth/v1/user", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

export async function getProfile(
  accessToken: string,
  userId: string,
): Promise<SupabaseProfile | null> {
  const params = new URLSearchParams({
    select:
      "id,email,approval_status,subscription_plan,subscription_expires_at,is_admin,created_at",
    id: `eq.${userId}`,
    limit: "1",
  });
  const rows = await supabaseRequest<SupabaseProfile[]>(
    `/rest/v1/profiles?${params.toString()}`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  return rows[0] ?? null;
}

export async function listProfiles(
  accessToken: string,
): Promise<SupabaseProfile[]> {
  const params = new URLSearchParams({
    select:
      "id,email,approval_status,subscription_plan,subscription_expires_at,is_admin,created_at",
    is_admin: "eq.false",
    order: "created_at.desc",
  });
  return supabaseRequest<SupabaseProfile[]>(
    `/rest/v1/profiles?${params.toString()}`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
}

export async function approveProfile(
  accessToken: string,
  userId: string,
  plan: string,
): Promise<SupabaseProfile | null> {
  const result = await supabaseRequest<SupabaseProfile | SupabaseProfile[]>(
    "/rest/v1/rpc/admin_set_subscription",
    {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ target_user_id: userId, plan }),
    },
  );
  return Array.isArray(result) ? result[0] ?? null : result;
}

export async function declineProfile(
  accessToken: string,
  userId: string,
): Promise<SupabaseProfile | null> {
  const result = await supabaseRequest<SupabaseProfile | SupabaseProfile[]>(
    "/rest/v1/rpc/admin_decline_user",
    {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ target_user_id: userId }),
    },
  );
  return Array.isArray(result) ? result[0] ?? null : result;
}