# NIH Credentials Sync

ECM is starting to port over logic from [ncbiaccess](https://github.com/broadinstitute/ncbiaccess), starting with the ability to periodically check for allowlist validity.

## Allowlist Validity Check
The validity check will run once-per-hour, and check that each validity list in the configured bucket is at most 12 hour old.
If it detects that an allowlist is older than 12 hours, it will log errors, and if told to fail-closed, wipe out any access to the resources governed by the allowlist.

## Environment Variables
The NIH Credentials Sync functionality requires the following variables to be set:
```text
ORCH_ADDRESS
NIH_ALLOWLIST_CHECK_CRON_STRING
NIH_ALLOWLIST_MANIFEST_PATH
NIH_ALLOWLIST_GOOGLE_BUCKET_PROJECT
NIH_ALLOWLIST_GOOGLE_BUCKET_NAME
NIH_ALLOWLIST_FAIL_CLOSED
```
There are default values configured for dev deployments, but for production releases, these env vars should be overridden. 
They can also be overridden for local testing.

By default, the NIH Credentials Allowlist check is disabled. To enable it, use `NIH_ALLOWLIST_CHECK_CRON_STRING=@hourly`.