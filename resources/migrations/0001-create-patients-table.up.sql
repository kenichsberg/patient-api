DROP TABLE IF EXISTS patients;
--;;
CREATE TABLE patients (
  id SERIAL PRIMARY KEY
  ,created_at TIMESTAMP NOT NULL DEFAULT now()
  ,updated_at TIMESTAMP NOT NULL DEFAULT now()
  ,first_name TEXT NOT NULL
  ,middle_name TEXT NOT NULL
  ,last_name TEXT NOT NULL
  ,gender BOOLEAN NOT NULL
  ,birth DATE NOT NULL
  ,address TEXT NOT NULL
  ,health_insurance_number TEXT NOT NULL UNIQUE CHECK (health_insurance_number ~ '^\d{12}$')
);
--;;
INSERT INTO patients (
  first_name
  ,middle_name
  ,last_name
  ,gender
  ,birth
  ,address
  ,health_insurance_number
) VALUES (
  'John'
  ,''
  ,'Smith'
  ,true
  ,'1990-01-01'
  ,'704 Hauser St., New York, N.Y.'
  ,'123456789012'
), (
  'Lois'
  ,'Di'
  ,'Nominator'
  ,true
  ,'1940-11-11'
  ,'Apartment 5A, 129 W. 81st St., New York, N.Y.'
  ,'234567890123'
), (
  'Minnie'
  ,'Van'
  ,'Ryder'
  ,false
  ,'1970-06-22'
  ,'485 Maple Drive, Mayfield, U.S.'
  ,'345678901234'
);
