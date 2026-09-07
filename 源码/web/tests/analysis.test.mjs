import test from 'node:test'
import assert from 'node:assert/strict'
import { createLatestRequestGate } from '../src/utils/latestRequest.mjs'
import { parseAnalysisEvidence } from '../src/utils/analysisEvidence.mjs'

test('new range aborts and invalidates both old report and enhancement', () => {
  const gate = createLatestRequestGate()
  const oldReport = gate.next()
  const enhancement = gate.next()
  assert.equal(oldReport.current(), false)
  const newReport = gate.next()
  assert.equal(enhancement.signal.aborted, true)
  assert.equal(enhancement.current(), false)
  assert.equal(newReport.current(), true)
  gate.cancel()
  assert.equal(newReport.current(), false)
})
test('evidence route preserves allowed filters but never accepts family scope', () => {
  const result = parseAnalysisEvidence({fromAnalysis:'1',startDate:'2026-08-01',endDate:'2026-08-31',memberId:'2',categoryId:'3',type:'2',familyId:'999'})
  assert.deepEqual(result,{startDate:'2026-08-01',endDate:'2026-08-31',memberId:2,categoryId:3,type:2})
})
test('invalid or reversed date filters are rejected', () => {
  for (const [startDate,endDate] of [['2026-02-30','2026-03-01'],['2026-09-01','2026-08-31'],['bogus','2026-08-31']])
    assert.equal(parseAnalysisEvidence({fromAnalysis:'1',startDate,endDate}),null)
})
