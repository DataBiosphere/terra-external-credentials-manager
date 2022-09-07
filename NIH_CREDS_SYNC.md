# NIH Credentials Sync

ECM will temporarily check the results of [ncbiaccess](https://github.com/broadinstitute/ncbiaccess).
It will periodically check for allowlist validity, ensuring that we don't have a security incident if an allowlist isn't updated in time.

## Allowlist Validity Check
The validity check will run once-per-hour, and check that each validity list in the configured bucket is at most 24 hours old.
If it detects that an allowlist is older than 24 hours, it will log errors, and if told to fail-closed, wipe out any access to the resources governed by the allowlist.

If ECM fails-closed, a manual run of `NCBIAccess` will restore access to rightful users.

## Environment Variables
The NIH Credentials Sync functionality requires the following variables to be set:
```text
ORCH_ADDRESS
NIH_ALLOWLIST_SYNC_FREQUENCY
NIH_ALLOWLIST_MANIFEST_PATH
NIH_ALLOWLIST_GOOGLE_BUCKET_PROJECT
NIH_ALLOWLIST_GOOGLE_BUCKET_NAME
NIH_ALLOWLIST_FAIL_CLOSED
```
There are default values configured for dev deployments, but for production releases, these env vars should be overridden. 
They can also be overridden for local testing.

By default, the NIH Credentials Allowlist check is disabled. To enable it, use `NIH_ALLOWLIST_SYNC_FREQUENCY=@hourly`.