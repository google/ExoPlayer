# DVB Test Data

The `.bin` files in this directory are generated from the `.xml` files using
`tstabcomp` from [TSDuck](https://tsduck.io/).

The XML files are kept to make it clear where the values in the test assertions
are coming from, and to make it easier to change or add data in future. When
adding new files, or making changes to existing ones, you should regenerate the
`.bin` files using the command above before committing.

To regenerate all the `.bin` files:

```shell
$  tstabcomp -c testdata/src/test/assets/dvbsi/*.xml
```
