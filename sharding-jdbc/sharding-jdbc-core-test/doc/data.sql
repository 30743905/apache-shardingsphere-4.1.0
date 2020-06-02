
CREATE TABLE t_order_0 (`order_id` INT NOT NULL, `user_id` INT NOT NULL, `status` VARCHAR(45) NULL, PRIMARY KEY (`order_id`));
CREATE TABLE t_order_1 (`order_id` INT NOT NULL, `user_id` INT NOT NULL, `status` VARCHAR(45) NULL, PRIMARY KEY (`order_id`));
CREATE TABLE t_order_2 (`order_id` INT NOT NULL, `user_id` INT NOT NULL, `status` VARCHAR(45) NULL, PRIMARY KEY (`order_id`));
CREATE TABLE t_order_3 (`order_id` INT NOT NULL, `user_id` INT NOT NULL, `status` VARCHAR(45) NULL, PRIMARY KEY (`order_id`));


CREATE TABLE `t_order_item_0` (`order_item_id` INT NOT NULL, `order_id` INT NOT NULL, `user_id` INT NOT NULL, `status` VARCHAR(45) NULL, PRIMARY KEY (`order_item_id`));
CREATE TABLE `t_order_item_1` (`order_item_id` INT NOT NULL, `order_id` INT NOT NULL, `user_id` INT NOT NULL, `status` VARCHAR(45) NULL, PRIMARY KEY (`order_item_id`));
CREATE TABLE `t_order_item_2` (`order_item_id` INT NOT NULL, `order_id` INT NOT NULL, `user_id` INT NOT NULL, `status` VARCHAR(45) NULL, PRIMARY KEY (`order_item_id`));
CREATE TABLE `t_order_item_3` (`order_item_id` INT NOT NULL, `order_id` INT NOT NULL, `user_id` INT NOT NULL, `status` VARCHAR(45) NULL, PRIMARY KEY (`order_item_id`));



--ds0
INSERT INTO t_order_0 VALUES(1000, 10, 'init');
INSERT INTO t_order_1 VALUES(1001, 10, 'init');
-- ds1
INSERT INTO t_order_0 VALUES(1100, 11, 'init');
INSERT INTO t_order_1 VALUES(1101, 11, 'init');


分库：user_id 		ds$->{user_id % 2}
分表：order 			order_id 		t_order$->{order_id % 2}
分表：orderItem		order_id		t_order_item$->{order_id % 2}



INSERT INTO t_order_item VALUES(100000, 1000, 10, 'init');
INSERT INTO t_order_item VALUES(100001, 1000, 10, 'init');
INSERT INTO t_order_item VALUES(100100, 1001, 10, 'init');
INSERT INTO t_order_item VALUES(100101, 1001, 10, 'init');
INSERT INTO t_order_item VALUES(110000, 1100, 11, 'init');
INSERT INTO t_order_item VALUES(110001, 1100, 11, 'init');
INSERT INTO t_order_item VALUES(110100, 1101, 11, 'init');
INSERT INTO t_order_item VALUES(110101, 1101, 11, 'init');




