# Madek ZHdK ZAPI Sync

Fetches people from ZAPI and pushes them to Madek API V2.

## Usage

### Automated sync in production 

Schedule this: 

```
clojure -M -m madek.zapi-sync.core --sync-people
```

### Sync of inactive people

The regular `--sync-people` command will not sync infos for people which are inactive in both Madek and ZAPI. So run this once if there is a need to update them:

```
clojure -M -m madek.zapi-sync.core --sync-inactive-people
```

## Testing and debugging

There are testing commands which help to inspect ZAPI data without modifying Madek data. Data can be fetched filtered by ID and/or can be written to a temporary file, which can be used as a data source to be pushed to Madek. There is also a command to update one existing Madek person at once.

Run `clojure -M -m madek.zapi-sync.core --help` to get more info (look out for "testing" commands)

## Configuring

Run `clojure -M -m madek.zapi-sync.core --help` to get more info
