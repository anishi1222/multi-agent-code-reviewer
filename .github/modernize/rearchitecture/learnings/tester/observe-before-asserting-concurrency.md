# Observe Before Asserting Concurrency

A queueing test becomes deterministic when it observes the contender reaching the synchronization boundary before asserting that work is blocked.

## What Happened

In `multi-agent-code-reviewer/t14.1`, merely checking that a second `Future` was unfinished would
have been scheduler-dependent: the caller might not have started yet. The test instead subclasses
`Semaphore`, signals immediately before the second `acquire()`, and holds the first protected
operation on a latch. Only after both facts are observed does it assert one active operation and
an unfinished second future.

## Takeaway

For semaphore/lock queueing contracts, coordinate three facts: the first holder entered, the
contender reached acquisition, and the holder has not been released. Then assert the concurrency
cap and finally release in a `finally` block so a failed assertion cannot strand worker threads.

## History

- 2026-08-08 (multi-agent-code-reviewer/t14.1): initial
