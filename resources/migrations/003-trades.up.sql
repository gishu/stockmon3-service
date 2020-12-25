CREATE TABLE st3.trades (id int PRIMARY KEY, account_id int, 
trade_date date NOT NULL, type char(1) NOT NULL, 
stock varchar(20) NOT NULL, qty int, price numeric(12,2), currency varchar(3),
created_at timestamptz NOT NULL,
CONSTRAINT fk_account FOREIGN KEY(account_id) REFERENCES st3.accounts(id));