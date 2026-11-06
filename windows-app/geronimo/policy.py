"""Call approval rules shared by the Geronimo Windows app.

Incoming calls always require a deliberate user action. The outgoing
allowlist is deliberately separate and can never alter incoming behavior.
"""

from __future__ import annotations

from collections.abc import AbstractSet


def incoming_call_requires_approval() -> bool:
    """Incoming calls must always be manually approved."""
    return True


def outgoing_call_requires_approval(
    contact_id: str | None,
    allowlisted_contact_ids: AbstractSet[str],
) -> bool:
    """Return True unless this outgoing destination is on the local allowlist."""
    normalized_id = (contact_id or "").strip()
    if not normalized_id:
        return True
    return normalized_id not in allowlisted_contact_ids


def may_answer_incoming_call(user_approved: bool) -> bool:
    """Only an explicit approval action may authorize answering."""
    return user_approved is True
