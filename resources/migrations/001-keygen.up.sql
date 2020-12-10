CREATE TABLE keys (id serial PRIMARY KEY, entity varchar(20), next_id int);
INSERT INTO keys (entity, next_id) VALUES ('trade', 1);
INSERT INTO keys (entity, next_id) VALUES ('account', 1);
