# Geronimo Windows app

Requires Python 3.10+ on Windows.

From this directory:

```powershell
python -m geronimo.main
```

The first screen is intentionally a scaffold. It reports that the phone bridge is not connected and does not pretend to place, answer, or route calls. The call approval rules live in `geronimo/policy.py` and are covered by unit tests.
