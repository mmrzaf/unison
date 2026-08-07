# Notifications

A Notification is a recipient-specific product communication created from a governed Notification Type. Reading and external delivery are communication facts and do not directly mutate business state.

## Alpha Notification Type catalogue

The Alpha catalogue contains:

- Process Condition assigned or reassigned;
- Process Condition attention raised to urgent or critical;
- Process Condition monitoring review due;
- Process Round assigned, due, or overdue where product-defined delivery is warranted;
- Work Order assigned, released, and placed on hold;
- Work Request information required, accepted, and rejected;
- independent verification required;
- Scheduled Work overdue;
- Failure Event investigation or restoration action requiring attention;
- replenishment or Inventory shortfall requiring attention;
- Report completed and failed;
- Tenant Export completed and failed;
- Tenant Import completed and failed;
- Android synchronization conflict requiring User action.

Alpha delivery channels are in-app delivery in Dashboard and Admin, in-app
delivery in Android, and Android push as a delivery hint. Email, SMS, arbitrary
Tenant-defined triggers, and webhook delivery are not Alpha channels. Opening a
Notification navigates to the normal authorized workflow and never performs the
action.

## A Notification Type

Waiotech must use product-defined Notification Types rather than configurable notification rules.

A Notification Type is a product-defined communication contract for a governed product event.

It defines:

- triggering event;
- recipient-resolution rule;
- content structure;
- mandatory or optional nature;
- available delivery channels;
- deduplication behavior;
- related subject;
- available navigation or action behavior.

Tenants cannot define arbitrary event triggers, audience queries, scripts, or business actions.

## A Notification Preference

Waiotech must allow preferences only within product-defined bounds.

A Notification Preference records the recipient’s or Tenant’s allowed choices for a Notification Type.

Preferences may control:

- enabled optional notifications;
- permitted channels;
- digest versus immediate delivery;
- quiet or delivery periods where supported.

Preferences cannot disable mandatory Notification Types or weaken required product communication.

## Keep mandatory product communication non-optional

Waiotech must keep mandatory product communication non-optional.

Each Notification Type declares whether it is:

- mandatory;
- channel-configurable;
- optional;
- user-subscribable.

A mandatory Notification must remain available through at least its required product channel.

Users may configure optional channels only where permitted.

## A Notification

Waiotech must use Notification as a communication record, not as a duplicate workflow state.

A Notification is the recipient-visible record that a governed product event requires their awareness or attention.

It preserves:

- recipient;
- Notification Type;
- related subject;
- creation time;
- rendered content or rendering context;
- read state at evaluation time;
- action or navigation destination.

## Keep Notification state minimal

Waiotech must keep Notification state minimal under Waiotech Product Authority.

The recipient-visible lifecycle is:

```text
unread → read
```

States such as approved, completed, acted, or resolved belong to the related business subject, not the Notification.

## Keep delivery mechanics separate from recipient-visible business meaning

Waiotech must keep delivery mechanics separate from recipient-visible business meaning.

Email, push, or other channel delivery may have technical outcomes such as:

- queued;
- delivered;
- failed;
- bounced;
- retried.

Those outcomes do not redefine:

- whether the Notification exists;
- whether the business event occurred;
- whether the Notification is read.

## Use Notifications as entry points to normal authorized actions

Waiotech must use Notifications as entry points to normal authorized actions.

A Notification may link to or invoke a normal domain action, but that action must re-evaluate:

- lifecycle state at evaluation time;
- Permission effective at retrieval;
- separation-of-duty conditions at evaluation time;
- Requirements effective at evaluation time;
- blockers at evaluation time;
- policy effective at evaluation time.

A Notification does not preserve authority from the time it was created.

## Related documents
- [Reports](010-reports.md)
- [Attachments and evidence](030-attachments-and-evidence.md)
- [Android Work App](../../30-experience/040-android-work-app.md)
