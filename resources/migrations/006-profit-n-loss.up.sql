CREATE TABLE st3.profit_n_loss (
    id serial PRIMARY KEY, 
    account_id integer, buy_id integer, sale_id integer,
    qty integer, 
CONSTRAINT fk_account FOREIGN KEY(account_id) REFERENCES st3.accounts(id),
CONSTRAINT fk_buy FOREIGN KEY(buy_id) REFERENCES st3.trades(id),
CONSTRAINT fk_sale FOREIGN KEY(sale_id) REFERENCES st3.trades(id));