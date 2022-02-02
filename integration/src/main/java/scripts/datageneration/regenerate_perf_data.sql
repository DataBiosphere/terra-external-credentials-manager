-- Generate a random string of the correct length (to stand in for the JWTs)
CREATE FUNCTION generate_random_string(approximate_length int) RETURNS text 
LANGUAGE SQL AS $$
  SELECT string_agg (md5(random()::text), '') -- generate random hashes (of 32 chars each)
  FROM generate_series(1, approximate_length/32) -- repeat to generate the needed string length
$$;


-- Insert a semi-random linked account and passport with the given values.
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
  -- Insert a corresponding passport and visa
  INSERT INTO ga4gh_passport (id, linked_account_id, jwt, expires)
  VALUES (
    nextval('ga4gh_passport_id_seq'),
    currval('linked_account_id_seq'),
    generate_random_string(5000), 
    current_timestamp + interval '3000 year'
  );

  INSERT INTO ga4gh_visa (passport_id, visa_type, jwt, expires, issuer, token_type, last_validated)
  VALUES (
    currval('ga4gh_passport_id_seq'),
    'TestVisaType',
    generate_random_string(2500), 
    current_timestamp + interval '3000 year',
    'https://someissuer.com',
    'access_token',
    current_timestamp + interval '3000 year'
  );
$$;


-- delete all of the existing data
TRUNCATE linked_account, ga4gh_passport, ga4gh_visa;

-- Insert semi-randomized records
do $$
DECLARE
  counter integer := 0;
BEGIN
  WHILE counter < 10000 LOOP
    -- Insert a record with a random user id
    CALL insert_test_record(md5(random()::text), 'testExternalUserId');
    counter := counter + 1;
  END LOOP;
END$$;

-- Insert Linked Accounts and Passports for specific users that already exist in SAM
CALL insert_test_record('114925642006220098835', 'Scarlett.Flowerpicker@test.firecloud.org');


DROP PROCEDURE insert_test_record(text, text);
DROP FUNCTION generate_random_string(int);
