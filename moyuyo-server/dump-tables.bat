@echo off
D:\xampp\mysql\bin\mysql.exe -h 127.0.0.1 -P 3306 -u root -pTk9q3Lvxf7sBAmR48EYW2gHc moyuyo_dev -B -N -e "SHOW TABLES;" > D:\MOYUYOWPC\moyuyo-server\db-tables.txt
type D:\MOYUYOWPC\moyuyo-server\db-tables.txt
