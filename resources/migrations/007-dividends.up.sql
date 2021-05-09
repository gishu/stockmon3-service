INSERT INTO st3.keys (entity, next_id) VALUES ('dividend', 1);
CREATE TABLE st3.dividends (
    id serial PRIMARY KEY, 
    account_id integer, 
    "date" DATE NOT NULL, 
	stock varchar(20) NOT NULL,
	amount numeric(12,2) NOT NULL,
	currency char(3) NOT NULL, 
	notes varchar(30),
	created_at timestamptz NOT NULL,
CONSTRAINT fk_account FOREIGN KEY(account_id) REFERENCES st3.accounts(id)
);