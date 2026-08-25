import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
import { xchacha20poly1305 } from '@noble/ciphers/chacha.js';

const KEY_NAME = 'pm_local_message_key_v2';

function bytesToBase64(bytes: Uint8Array) {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return globalThis.btoa(s);
}

function base64ToBytes(s: string) {
  const bin = globalThis.atob(s);
  return Uint8Array.from(bin, c => c.charCodeAt(0));
}

async function getKey() {
  let encoded = await SecureStore.getItemAsync(KEY_NAME);
  if (!encoded) {
    const key = await Crypto.getRandomBytesAsync(32);
    encoded = bytesToBase64(key);
    await SecureStore.setItemAsync(KEY_NAME, encoded, {
      keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    });
  }
  return base64ToBytes(encoded);
}

/**
 * Device-local authenticated encryption using XChaCha20-Poly1305.
 * This protects plaintext from the database but is not yet multi-device
 * recipient E2EE. A production messenger needs authenticated identity keys,
 * key agreement and a ratchet protocol.
 */
export async function encryptText(text: string) {
  const key = await getKey();
  const nonce = await Crypto.getRandomBytesAsync(24);
  const ciphertext = xchacha20poly1305(key, nonce).encrypt(new TextEncoder().encode(text));
  return { ciphertext: bytesToBase64(ciphertext), nonce: bytesToBase64(nonce) };
}

export async function decryptText(ciphertext: string, nonce: string) {
  try {
    const key = await getKey();
    const plaintext = xchacha20poly1305(key, base64ToBytes(nonce)).decrypt(base64ToBytes(ciphertext));
    return new TextDecoder().decode(plaintext);
  } catch {
    return '[не удалось расшифровать]';
  }
}
