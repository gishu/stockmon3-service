ALTER TABLE st3.trades
ADD COLUMN notes varchar(30);
--;;
ALTER TABLE st3.holdings
ADD COLUMN price numeric(12,2), 
ADD COLUMN currency varchar(3);