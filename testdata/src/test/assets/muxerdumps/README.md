# Muxer dump files

To generate a new dump file or to update an existing one:

1. Change `DumpFileAsserts#DUMP_FILE_ACTION` to `WRITE_TO_LOCAL` (for Robolectric tests) or `WRITE_TO_DEVICE` (for Instrumentation tests).
2. Re-run the test.
3. Change `DumpFileAsserts#DUMP_FILE_ACTION` back to `COMPARE_WITH_EXISTING`.
