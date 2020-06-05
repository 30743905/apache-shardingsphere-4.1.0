CREATE TABLE t_order(
		order_id INT NOT NULL,
		user_id INT NOT NULL,
		status VARCHAR(45) NULL,
		mark VARCHAR(100) NULL,
		PRIMARY KEY (order_id)
);

CREATE TABLE t_order_item(
	order_item_id INT NOT NULL,
	order_id INT NOT NULL,
	user_id INT NOT NULL,
	status VARCHAR(45) NULL,
	mark VARCHAR(100) NULL,
	PRIMARY KEY (order_item_id)
);


