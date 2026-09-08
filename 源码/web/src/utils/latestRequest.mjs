// Abort 节约传输；序号校验保证即使服务端不支持取消，旧响应也不能覆盖新筛选。
export function createLatestRequestGate() {
  let sequence = 0
  let controller
  return {
    next() {
      controller?.abort()
      controller = new AbortController()
      const id = ++sequence
      return { signal: controller.signal, current: () => id === sequence }
    },
    cancel() { ++sequence; controller?.abort() }
  }
}
