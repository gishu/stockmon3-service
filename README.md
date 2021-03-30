# stockmon3

FIXME: description

## Installation

Download from http://example.com/FIXME.

## Usage

FIXME: explanation

    $ java -jar stockmon3-0.1.0-standalone.jar [args]

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2020 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.


# Schema Migrations

```clojure
;Schema up
(require '[stockmon3.migrations :as mig] '[ragtime.repl :as repl])
(repl/migrate mig/config)

;Schema down
(let [total-migrations (-> mig/config :migrations count)]
    (repl/rollback mig/config total-migrations))
```

# DB 

## Backup
`pg_dump [DBName] > outfile.dmp`

## Restore
`psql -U [username] -d [DBName] < outfile.dmp`
 
# Docker
```
# Build the images from within the folders containing the Dockerfile
docker image build --tag mx-postgres:latest .


docker network create \
  --driver bridge \
  --label project=stockmon3 \
  --attachable \
  --scope local \
  --subnet 10.0.42.0/24 \
  --ip-range 10.0.42.128/25 \
  stockmon3-network


docker run -d \
    --name st3-pgsql \
    --network stockmon3-network \
    -e POSTGRES_DB=kaching \
    -e POSTGRES_USER=gishu \
    -e POSTGRES_PASSWORD=supersecretpwd \
    -e PGDATA=/var/lib/postgresql/data/pgdata \
    -v ~/dockervol/st3-data:/var/lib/postgresql/data \
    postgres-mx


docker run -d \
    --name st3-svc \
    --network stockmon3-network \
    -p 8123:8000 \
    -e STOCKMON_DB_HOST=st3-pgsql \
    -e STOCKMON_DB=kaching \
    -e STOCKMON_DB_USER=gishu \
    -e STOCKMON_DB_PWD=supersecretpwd \
    st3-service

docker run -d \
    --name st3-ui \
    --network stockmon3-network \
    -p 8888:80 \
    st3-ui

```