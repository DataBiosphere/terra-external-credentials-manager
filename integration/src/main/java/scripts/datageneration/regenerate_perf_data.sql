-- This function inserts a semi-random linked account and passport with
-- the given user_id and external_user_id
CREATE PROCEDURE insert_test_record(user_id text, external_user_id text)  
LANGUAGE SQL 
AS $$ 
    -- Insert a linked account with the given values
    INSERT INTO linked_account 
    (id, user_id, provider_name, refresh_token, expires, external_user_id, is_authenticated)
    VALUES (
        nextval('linked_account_id_seq'), 
        user_id, 
        'ras', 
        'testToken', 
        current_timestamp + interval '3000 year', 
        external_user_id, 
        true
    );
    -- Insert a corresponding passport
        INSERT INTO ga4gh_passport (linked_account_id, jwt, expires)
        VALUES (
            currval('linked_account_id_seq'), 
            'testJwt', 
            current_timestamp + interval '3000 year'
        );
$$;  


-- delete all of the existing data
TRUNCATE linked_account, ga4gh_passport, ga4gh_visa;

-- Insert semi-randomized records
do $$
declare 
    counter integer := 0;
begin
    while counter < 10000 loop
        -- Insert a record with a random user id 
        CALL insert_test_record(substr(md5(random()::text), 0, 25), 'testExternalUserId');
        counter := counter + 1;
   end loop;
end$$;

-- Insert Linked Accounts and Passports for specific users that already exist in SAM
CALL insert_test_record('114925642006220098835', 'Scarlett.Flowerpicker@test.firecloud.org');


DROP PROCEDURE insert_test_record(text, text);
