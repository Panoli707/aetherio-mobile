const MAX_REUSABLE_HOME_CACHE_AGE_MS = 7 * 24 * 60 * 60 * 1_000;

export function isReusableHomeCache(updatedAt: number, now = Date.now()) {
  return updatedAt > 0 && now - updatedAt < MAX_REUSABLE_HOME_CACHE_AGE_MS;
}

export function normalizeDailyRowsSignature(signature: string) {
  return signature
    .replace(/\|d:\d{4}-\d{2}-\d{2}/g, "")
    .replace(/(\|aetherio-starter\|)\d{4}-\d{2}-\d{2}(\|)/, "$1$2");
}

export function normalizeDailyHeroSignature(signature: string) {
  return signature.replace(/^\d{4}-\d{2}-\d{2}(?=\|)/, "");
}
