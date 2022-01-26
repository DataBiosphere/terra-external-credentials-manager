
-- delete all of the existing data
TRUNCATE linked_account, ga4gh_passport, ga4gh_visa;

-- Insert Linked Accounts and Passports for specific users that already exist in SAM
INSERT INTO linked_account 
(id, user_id, provider_name, refresh_token, expires, external_user_id, is_authenticated)
VALUES
(nextval(pg_get_serial_sequence('linked_account','id')), '114925642006220098835', 'ras', 'testToken', current_timestamp + interval '3000 year', 'Scarlett.Flowerpicker@test.firecloud.org', true);

INSERT INTO ga4gh_passport (linked_account_id, jwt, expires)
VALUES 
(currval(pg_get_serial_sequence('linked_account','id')), 'testJwt', current_timestamp + interval '3000 year');

-- Insert semi-randomized records
do $$
declare 
    counter integer := 0;
begin
    while counter < 10000 loop
        -- Insert a linked account with a random user_id
        INSERT INTO linked_account 
        (id, user_id, provider_name, refresh_token, expires, external_user_id, is_authenticated)
        VALUES (
            nextval(pg_get_serial_sequence('linked_account','id')), 
            substr(md5(random()::text), 0, 25), 
            'ras', 
            'testToken', 
            current_timestamp + interval '3000 year', 
            'testExternalUserId', 
            true
            );

        -- Insert a corresponding passport
        INSERT INTO ga4gh_passport (linked_account_id, jwt, expires)
        VALUES (
            currval(pg_get_serial_sequence('linked_account','id')), 
            'testJwt', 
            current_timestamp + interval '3000 year'
        );
	  
        counter := counter + 1;
   end loop;
end$$;

-- TODO: decide if visas should be generated as well
-- TODO: determine how the db is indexed - does the order of insertion matter here?
-- TODO: try making a function which inserts a LA & Passport given a user_id and external_user_id (so there's less duplicate code)
-- TODO: make sure while loop actually works