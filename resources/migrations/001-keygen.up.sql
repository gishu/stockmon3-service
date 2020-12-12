CREATE SCHEMA st3;
CREATE TABLE st3.keys (id serial PRIMARY KEY, entity varchar(20), next_id int);
INSERT INTO st3.keys (entity, next_id) VALUES ('trade', 1);
INSERT INTO st3.keys (entity, next_id) VALUES ('account', 1);
