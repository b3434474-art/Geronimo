import unittest

from geronimo.policy import (
    incoming_call_requires_approval,
    may_answer_incoming_call,
    outgoing_call_requires_approval,
)


class CallPolicyTests(unittest.TestCase):
    def test_incoming_calls_always_require_approval(self):
        self.assertTrue(incoming_call_requires_approval())

    def test_allowlisted_contact_skips_outgoing_confirmation(self):
        self.assertFalse(
            outgoing_call_requires_approval("contact-123", {"contact-123"})
        )

    def test_unknown_outgoing_contact_requires_approval(self):
        self.assertTrue(
            outgoing_call_requires_approval("contact-456", {"contact-123"})
        )

    def test_missing_outgoing_contact_requires_approval(self):
        self.assertTrue(outgoing_call_requires_approval(None, set()))
        self.assertTrue(outgoing_call_requires_approval("  ", set()))

    def test_incoming_call_can_only_be_answered_after_approval(self):
        self.assertFalse(may_answer_incoming_call(False))
        self.assertTrue(may_answer_incoming_call(True))


if __name__ == "__main__":
    unittest.main()
