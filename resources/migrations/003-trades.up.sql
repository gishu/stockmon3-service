CREATE TABLE st3.trades (id int PRIMARY KEY, account_id int, 
trade_date date NOT NULL, type char(1) NOT NULL, 
stock varchar(20) NOT NULL, qty int NOT NULL, price numeric(12,2) NOT NULL,
charges numeric(12,2) NOT NULL, currency char(3) NOT NULL, trade_value numeric(12,2) NOT NULL,
created_at timestamptz NOT NULL,
CONSTRAINT fk_account FOREIGN KEY(account_id) REFERENCES st3.accounts(id));