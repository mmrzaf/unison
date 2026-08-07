# Accepted maintenance need

Accepted maintenance need is represented by Work Order authority. Work Request, Finding, Process Condition, Failure Event, inspection evidence, and direct authorized creation may all provide evidence or origin without becoming alternate accepted-work records.

## Define acceptance through Work Order representation

A maintenance need becomes accepted work only when one or more Work Orders authoritatively represent the accepted maintenance response.

There must be no observable state in which a Work Request is marked accepted but its accepted maintenance need is not represented by a Work Order.

The same principle applies when accepted work originates from Process or Reliability: Process Condition and Failure Event remain authoritative source records, while Work Order owns accepted maintenance work.

## Keep evidence in its owning context

Waiotech deliberately uses several evidence concepts with distinct meaning:

- Process Reading records a structured operational value at a Measurement Point;
- Operational Observation records qualitative Process evidence;
- Inspection Result records structured inspection evidence;
- Finding records a discovered maintainable condition requiring explicit Maintenance disposition;
- Failure Event records actual functional failure and consequence;
- Work Request records an unaccepted proposed maintenance need;
- Work Order records accepted maintenance work and execution evidence.

These records may reference one another but must not be silently converted or rewritten as one generic Observation or maintenance-need aggregate.

## The minimum maintenance need model

```text
Process Condition
    → no maintenance / monitor / Work Request / related Work Order

Inspection Result
    → no Finding / Finding

Finding
    → resolved here / no action / monitor / Work Request / Work Order / duplicate / invalid or corrected

Work Request
    → triage → rejected / cancelled / deferred / more information required / accepted into Work Order

Failure Event
    → zero or more Work Requests and one or more Work Orders as response requires

Direct authorized creation
    → Work Order
```

The distinctions are:

- evidence may establish that something happened without establishing accepted maintenance work;
- Work Request permits accountable triage before acceptance;
- Work Order is the only representation of accepted maintenance work;
- source records remain historically intact after Maintenance responds.

## Related documents

- [Work Requests](030-work-requests.md)
- [Findings](090-findings.md)
- [Process Conditions](../35-process/020-process-conditions.md)
- [Failure Events](../55-reliability/010-failure-events.md)
