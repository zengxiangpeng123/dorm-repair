import { afterEach, describe, expect, it, vi } from 'vitest'
import { STATUS, STATUS_ORDER, WALK_STATUS, human, shortLeft, shortWindow, steps, timerLabel } from './status'
import { parseAt, timersOf, toneOf } from './timers'

afterEach(() => vi.useRealTimers())

describe('status constants', () => {
  it('keeps exactly nine workflow states', () => {
    expect(STATUS_ORDER).toHaveLength(9)
    expect(STATUS.VERIFYING).toBe('待验收')
    expect(STATUS.REWORKING).toBe('返修中')
  })

  it('keeps verification and later stages out of the walk queue', () => {
    expect(WALK_STATUS).toEqual(['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS'])
  })

  it('maps a verifying order to the fourth step', () => {
    const states = steps({ status: 'VERIFYING' }).map((s) => s.state)
    expect(states).toEqual(['done', 'done', 'done', 'now', 'pending'])
  })

  it('formats visit windows and countdowns', () => {
    expect(shortWindow('上午 08:00-12:00（学生有课）')).toBe('上午 08:00-12:00')
    expect(shortLeft(0)).toBe('已超时')
    expect(shortLeft(2 * 86400000 + 3 * 3600000)).toBe('2d3h')
    expect(human(90 * 60000)).toBe('1 小时 30 分')
  })

  it('labels T1/T2 consistently', () => {
    expect(timerLabel({ kind: 'T1' })).toBe('验收倒计时')
    expect(timerLabel({ kind: 'T2' })).toBe('归档倒计时')
  })
})

describe('timer calculations', () => {
  it('parses server-local timestamps without relying on UTC strings', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 18, 12, 0, 0))
    const parsed = parseAt('2026-11-18 10:00:00')
    expect(parsed).toBe(new Date(2026, 10, 18, 10, 0, 0).getTime())
  })

  it('computes T1 from finishAt and T2 from signAt independently', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 18, 12, 0, 0))
    const verifying = timersOf({ status: 'VERIFYING', finishAt: '2026-11-18 10:00:00' }, { verifyHours: 168 })
    expect(verifying.t1.label).toBe('验收倒计时')
    expect(verifying.t1.leftMs).toBe(166 * 3600000)
    expect(verifying.t2).toBeNull()

    const done = timersOf({ status: 'DONE', signAt: '2026-11-18 10:00:00' }, { archiveDays: 7 })
    expect(done.t2.label).toBe('归档倒计时')
    expect(done.t2.leftMs).toBe(7 * 86400000 - 2 * 3600000)
    expect(done.t1).toBeNull()
  })

  it('turns near-expiry timers into warning tones', () => {
    expect(toneOf(0, 100)).toBe('bad')
    expect(toneOf(20, 100)).toBe('warn')
    expect(toneOf(80, 100)).toBe('')
  })
})
