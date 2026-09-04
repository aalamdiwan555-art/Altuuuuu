import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import type { Request, Response } from "express";

const COOKIE_NAME = "altuu_admin_session";
const COOKIE_MAX_AGE_MS = 1000 * 60 * 60 * 8;

export type AdminSession = {
  accessToken: string;
  refreshToken: string | null;
};

function getEncryptionKey(): Buffer {
  const secret = process.env.SESSION_SECRET;
  if (!secret) {
    throw new Error("SESSION_SECRET must be configured for admin sessions.");
  }
  return createHash("sha256").update(secret).digest();
}

export function writeAdminSession(res: Response, session: AdminSession): void {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", getEncryptionKey(), iv);
  const encrypted = Buffer.concat([
    cipher.update(JSON.stringify(session), "utf8"),
    cipher.final(),
  ]);
  const payload = Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString(
    "base64url",
  );

  res.cookie(COOKIE_NAME, payload, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    maxAge: COOKIE_MAX_AGE_MS,
    path: "/",
  });
}

export function clearAdminSession(res: Response): void {
  res.clearCookie(COOKIE_NAME, { path: "/" });
}

export function readAdminSession(req: Request): AdminSession | null {
  const rawCookie = req.headers.cookie
    ?.split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${COOKIE_NAME}=`))
    ?.slice(COOKIE_NAME.length + 1);

  if (!rawCookie) return null;

  try {
    const payload = Buffer.from(rawCookie, "base64url");
    if (payload.length < 29) return null;
    const iv = payload.subarray(0, 12);
    const authTag = payload.subarray(12, 28);
    const encrypted = payload.subarray(28);
    const decipher = createDecipheriv("aes-256-gcm", getEncryptionKey(), iv);
    decipher.setAuthTag(authTag);
    const decoded = Buffer.concat([
      decipher.update(encrypted),
      decipher.final(),
    ]).toString("utf8");
    const session = JSON.parse(decoded) as Partial<AdminSession>;

    if (
      typeof session.accessToken !== "string" ||
      !session.accessToken ||
      (session.refreshToken !== null &&
        typeof session.refreshToken !== "string")
    ) {
      return null;
    }

    return {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken ?? null,
    };
  } catch {
    return null;
  }
}