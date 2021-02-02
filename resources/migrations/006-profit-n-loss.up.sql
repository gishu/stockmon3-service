CREATE TABLE st3.profit_n_loss (
    id serial PRIMARY KEY, 
    account_id integer, 
    sale_date DATE NOT NULL, buy_id integer, sale_id integer,
    qty integer NOT NULL,
    charges numeric(12,2) NOT NULL, gain numeric(12,2) NOT NULL, currency char(3) NOT NULL,
    duration_days integer,
CONSTRAINT fk_account FOREIGN KEY(account_id) REFERENCES st3.accounts(id),
CONSTRAINT fk_buy FOREIGN KEY(buy_id) REFERENCES st3.trades(id),
CONSTRAINT fk_sale FOREIGN KEY(sale_id) REFERENCES st3.trades(id));
--;;
CREATE VIEW st3.vw_pnl_report AS
(select gains.id, gains.account_id, gains.sale_date, b.stock, 
	gains.qty, b.price cost_price, s.price sale_price, 
	(gains.qty * b.price) tco,
	gains.charges, gain, gains.currency, duration_days, 
	CASE 
	  WHEN duration_days >= 365 THEN 'LT'
	  ELSE 'ST'
	END As Age
from st3.profit_n_loss gains
join st3.trades b on gains.buy_id = b.id
join st3.trades s on gains.sale_id = s.id
order by id)