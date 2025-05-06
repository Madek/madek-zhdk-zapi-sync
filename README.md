# Madek ZHdK ZAPI Sync

Fetches people from ZAPI and pushes them to Madek API V2.

## Usage

### Automated sync in production 

Schedule this: 

```
clojure -M -m madek.zapi-sync.core --sync-people
```

### History sync

`--sync-people` will not sync infos for people which are inactive in ZAPI (`is_zhdk: false`). So after the first sync (or after changes to the sync were made), this command should be run once: 

```
clojure -M -m madek.zapi-sync.core --sync-inactive-people
```

## Testing and debugging

There are commands which help to inspect ZAPI data without syncing. Data can be fetched filtered by ID and/or can be written to a temporary file, which can be used as a data source to be pushed to Madek. There is also a command to update one existing Madek person at once.

Run `clojure -M -m madek.zapi-sync.core --help` to get more info (look out for "testing" commands)

## Configuring

Run `clojure -M -m madek.zapi-sync.core --help` to get more info
