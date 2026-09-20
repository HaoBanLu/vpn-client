/** 建隧 / 刚连上时抑制「网络异常」全局 toast，避免已保护却吓人。 */
let quietUntilMs = 0

export function quietNetworkErrorToasts(durationMs = 12_000, now = Date.now()) {
  quietUntilMs = Math.max(quietUntilMs, now + durationMs)
}

export function shouldQuietNetworkErrorToast(now = Date.now()): boolean {
  return now < quietUntilMs
}

/** 仅单测用 */
export function resetNetworkErrorToastQuietForTests() {
  quietUntilMs = 0
}
