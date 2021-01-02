CREATE TABLE st3.holdings (
    id serial PRIMARY KEY, 
    account_id integer, buy_id integer, 
    rem_qty integer, 
CONSTRAINT fk_account FOREIGN KEY(account_id) REFERENCES st3.accounts(id),
CONSTRAINT fk_trades FOREIGN KEY(buy_id) REFERENCES st3.trades(id));