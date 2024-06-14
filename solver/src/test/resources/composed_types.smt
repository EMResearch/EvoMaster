(declare-datatypes () ((ProductsRow (price-min_price-stock-user_id (PRICE Int) (MIN_PRICE Int) (STOCK Int) (USER_ID Int) ))))
(declare-const products1 ProductsRow)
(declare-const products2 ProductsRow)
(assert (> (MIN_PRICE products1) 1))
(assert (> (MIN_PRICE products2) 1))
(assert (or (>= (STOCK products1) 5) (= (STOCK products1) 100) ))
(assert (or (>= (STOCK products2) 5) (= (STOCK products2) 100) ))
(assert (and (> (PRICE products1) 100) (< (PRICE products1) 9999)))
(assert (and (> (PRICE products2) 100) (< (PRICE products2) 9999)))
(declare-datatypes () ((UsersRow (id-name-age-points (ID Int) (NAME String) (AGE Int) (POINTS Int) ))))
(declare-const users1 UsersRow)
(declare-const users2 UsersRow)
(assert (= (NAME users1) "agus"))
(assert (= (NAME users2) "agus"))
(assert (or (< (POINTS users1) 4) (> (POINTS users1) 6) ))
(assert (or (< (POINTS users2) 4) (> (POINTS users2) 6) ))
(assert (and (> (AGE users1) 18) (< (AGE users1) 100)))
(assert (and (> (AGE users2) 18) (< (AGE users2) 100)))
(assert (<= (POINTS users1) 10))
(assert (<= (POINTS users2) 10))
(assert (>= (POINTS users1) 0))
(assert (>= (POINTS users2) 0))
(assert (or (= (USER_ID products1) (ID users1)) (= (USER_ID products1) (ID users2)) ))
(assert (or (= (USER_ID products2) (ID users1)) (= (USER_ID products2) (ID users2)) ))
(assert (distinct (ID users1) (ID users2)))
(assert (and (> (AGE users1) 30) (= (POINTS users1) 7)))
(assert (and (> (AGE users2) 30) (= (POINTS users2) 7)))
(check-sat)
(get-value (products1))
(get-value (products2))
(get-value (users1))
(get-value (users2))