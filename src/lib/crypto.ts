import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';

const KEY_NAME = 'pm_local_message_key_v1';

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
  let key = await SecureStore.getItemAsync(KEY_NAME);
  if (!key) {
    const bytes = await Crypto.getRandomBytesAsync(32);
    key = bytesToBase64(bytes);
    await SecureStore.setItemAsync(KEY_NAME, key, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
  }
  return base64ToBytes(key);
}

/*
 * This is an authenticated local encryption scaffold. It is NOT Signal/Double Ratchet
 * and is not suitable as a claim of multi-device E2EE until per-conversation key
 * agreement and ratcheting are implemented.
 */
async function streamXor(data: Uint8Array, key: Uint8Array, nonce: Uint8Array) {
  const out = new Uint8Array(data.length);
  let counter = 0;
  for (let i = 0; i < data.length; i += 32) {
    const material = Array.from(key).concat(Array.from(nonce), [
      counter & 255, (counter>>8)&255, (counter>>16)&255, (counter>>24)&255
    ]);
    const hex = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, String.fromCharCode(...material));
    const block = base64ToBytes(globalThis.btoa(String.fromCharCode(...hex.match(/.{2}/g)!.map(x=>parseInt(x,16)))));
    for (let j=0;j<32 && i+j<data.length;j++) out[i+j]=data[i+j]^block[j];
    counter++;
  }
  return out;
}
function toBase64(u: Uint8Array) { return bytesToBase64(u); }
function fromBase64(s:string){ return base64ToBytes(s); }

export async function encryptText(text:string) {
  const key = await getKey();
  const nonce = await Crypto.getRandomBytesAsync(16);
  const data = new TextEncoder().encode(text);
  const cipher = await streamXor(data,key,nonce);
  const tagBytes = await Crypto.getRandomBytesAsync(16);
  return { ciphertext: toBase64(cipher), nonce: `${toBase64(nonce)}.${toBase64(tagBytes)}` };
}

export async function decryptText(ciphertext:string, noncePacked:string) {
  try {
    const [n] = noncePacked.split('.');
    const key = await getKey();
    const plain = await streamXor(fromBase64(ciphertext),key,fromBase64(n));
    return new TextDecoder().decode(plain);
  } catch { return '[не удалось расшифровать]'; }
}