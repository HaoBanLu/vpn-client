/**
 * 连接进行中时，后端仍可能回报上一轮的 disconnected / failed；
 * 此时不应冲掉前端「连接中」态，否则会闪一下「连接失败」再变成「已连接」。
 */
export function shouldIgnoreDisconnectedWhileConnecting(input: {
  connectPending: boolean
  connectionState: string
  isSwitching: boolean
  userInitiatedDisconnect: boolean
  nextState: string
}): boolean {
  const inFlight =
    input.connectPending ||
    input.connectionState === 'connecting' ||
    input.isSwitching
  if (!inFlight || input.userInitiatedDisconnect) return false
  return input.nextState === 'disconnected' || input.nextState === 'failed'
}
