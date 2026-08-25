import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
import nacl from 'tweetnacl';

const KEY_NAME = 'pm_identity_secret_v1';

function toBase64(bytes: Uint8Array) {
  return btoa(String.fromCharCode(...bytes));
}
function fromBase64(value: string) {
  return Uint8Array.from(atob(value), c => c.charCodeAt(0));
}

export async function getOrCreateIdentity() {
  const existing = await SecureStore.getItemAsync(KEY_NAME);
  if (existing) return fromBase64(existing);
  const secret = nacl.randomBytes(32);
  await SecureStore.setItemAsync(KEY_NAME, toBase64(secret), {
    requireAuthentication: false,
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY
  });
  return secret;
}

export async function encryptForStorage(plaintext: string) {
  const key = await getOrCreateIdentity();
  const nonce = nacl.randomBytes(nacl.secretbox.nonceLength);
  const data = new TextEncoder().encode(plaintext);
  const box = nacl.secretbox(data, nonce, key);
  return { ciphertext: toBase64(box), nonce: toBase64(nonce) };
}

export async function decryptFromStorage(ciphertext: string, nonce: string) {
  const key = await getOrCreateIdentity();
  const opened = nacl.secretbox.open(fromBase64(ciphertext), fromBase64(nonce), key);
  if (!opened) throw new Error('Unable to decrypt message');
  return new TextDecoder().decode(opened);
}

export async function generateDeviceId() {
  return Crypto.randomUUID();
}
