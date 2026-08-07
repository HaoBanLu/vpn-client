/**
 * 连接进行中时，后端仍可能回报 disconnected；此时不应冲掉前端「连接中」态。
 * 与 Android connectPending 期间保持 CONNECTING UI 一致。
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
  return (
    inFlight &&
    input.nextState === 'disconnected' &&
    !input.userInitiatedDisconnect
  )
}
