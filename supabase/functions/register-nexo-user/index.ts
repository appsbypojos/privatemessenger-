import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Content-Type": "application/json"
};

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const secretKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY");
if (!secretKey) throw new Error("Supabase secret key is not configured");

const admin = createClient(supabaseUrl, secretKey, {
  auth: { autoRefreshToken: false, persistSession: false }
});

const authEmail = (username: string) => `${username}@auth.nexo.app`;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers });
  if (req.method !== "POST") return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405, headers });

  try {
    const { username, password } = await req.json();
    const nick = String(username ?? "").trim().toLowerCase();
    const pass = String(password ?? "");

    if (!/^[a-z0-9_]{3,24}$/.test(nick)) {
      return new Response(JSON.stringify({ error: "Ник: 3–24 символа, только a-z, 0-9 и _" }), { status: 400, headers });
    }
    if (pass.length < 6 || pass.length > 72) {
      return new Response(JSON.stringify({ error: "Пароль должен содержать 6–72 символа" }), { status: 400, headers });
    }

    const { data: existing, error: lookupError } = await admin
      .from("profiles")
      .select("id")
      .eq("username", nick)
      .maybeSingle();
    if (lookupError) throw lookupError;
    if (existing) return new Response(JSON.stringify({ error: "Этот ник уже занят" }), { status: 409, headers });

    const { data, error } = await admin.auth.admin.createUser({
      email: authEmail(nick),
      password: pass,
      email_confirm: true,
      user_metadata: { username: nick, display_name: nick }
    });
    if (error) {
      if (/already|registered/i.test(error.message)) {
        return new Response(JSON.stringify({ error: "Этот ник уже занят" }), { status: 409, headers });
      }
      throw error;
    }

    return new Response(JSON.stringify({ ok: true, user_id: data.user?.id, username: nick }), { status: 201, headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error instanceof Error ? error.message : "Ошибка регистрации" }), { status: 500, headers });
  }
});
