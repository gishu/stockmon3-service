CREATE SCHEMA st3;
CREATE TABLE st3.keys (entity varchar(20) PRIMARY KEY, next_id int);
INSERT INTO st3.keys (entity, next_id) VALUES ('trade', 1);
INSERT INTO st3.keys (entity, next_id) VALUES ('account', 1);
