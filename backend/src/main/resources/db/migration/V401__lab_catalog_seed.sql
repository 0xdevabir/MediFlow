-- T07 Lab: starter test catalogue, reference ranges and direct-test packages.
-- direct_allowed follows R&D v1.3 §5: simple tests (blood sugar, CBC, lipid profile, urine routine,
-- HbA1c) may be booked without a doctor; tests that need interpretation, imaging, special
-- preparation or carry higher risk always need a doctor's order. The final list is set by the
-- hospital with its clinical advisor (admin, T11). Prices in BDT. Ranges are adult defaults.

INSERT INTO lab_test (code, name, loinc_code, direct_allowed, price, active) VALUES
  ('FBS',      'Fasting blood glucose',       '1558-6',  TRUE,  150.00, TRUE),
  ('RBS',      'Random blood glucose',        '15074-8', TRUE,  150.00, TRUE),
  ('HBA1C',    'HbA1c',                       '4548-4',  TRUE,  900.00, TRUE),
  ('CBC',      'Complete blood count',        '58410-2', TRUE,  400.00, TRUE),
  ('LIPID',    'Lipid profile',               '57698-3', TRUE,  800.00, TRUE),
  ('URINE_RE', 'Urine routine examination',   '24357-6', TRUE,  200.00, TRUE),
  ('CREAT',    'Serum creatinine',            '2160-0',  FALSE, 350.00, TRUE),
  ('K',        'Serum potassium',             '2823-3',  FALSE, 400.00, TRUE),
  ('TROP_I',   'Troponin I',                  '10839-9', FALSE, 1500.00, TRUE),
  ('TSH',      'Thyroid stimulating hormone', '3016-3',  FALSE, 700.00, TRUE),
  ('XRAY_CHEST','Chest X-ray (PA view)',      '36643-5', FALSE, 500.00, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO lab_reference_range
  (test_id, analyte_code, analyte_name, loinc_code, ucum_unit, sex, ref_low, ref_high, critical_low, critical_high, sort_order)
SELECT t.id, r.analyte_code, r.analyte_name, r.loinc_code, r.ucum_unit, r.sex, r.ref_low, r.ref_high, r.critical_low, r.critical_high, r.sort_order
FROM (VALUES
  ('FBS',      'GLU_F', 'Glucose (fasting)',        '1558-6',  'mmol/L',   NULL, 3.9,   5.5,   2.8,  22.2, 1),
  ('RBS',      'GLU_R', 'Glucose (random)',         '15074-8', 'mmol/L',   NULL, 3.9,   7.8,   2.8,  22.2, 1),
  ('HBA1C',    'HBA1C', 'Haemoglobin A1c',          '4548-4',  '%',        NULL, 4.0,   5.6,   NULL, NULL, 1),
  ('CBC',      'HGB',   'Haemoglobin',              '718-7',   'g/dL',     'M',  13.0,  17.0,  7.0,  20.0, 1),
  ('CBC',      'HGB',   'Haemoglobin',              '718-7',   'g/dL',     'F',  12.0,  15.0,  7.0,  20.0, 1),
  ('CBC',      'HGB',   'Haemoglobin',              '718-7',   'g/dL',     NULL, 12.0,  17.0,  7.0,  20.0, 1),
  ('CBC',      'WBC',   'White cell count',         '6690-2',  '10*3/uL',  NULL, 4.0,   11.0,  2.0,  30.0, 2),
  ('CBC',      'PLT',   'Platelets',                '777-3',   '10*3/uL',  NULL, 150.0, 450.0, 50.0, 1000.0, 3),
  ('LIPID',    'CHOL',  'Total cholesterol',        '2093-3',  'mg/dL',    NULL, NULL,  200.0, NULL, NULL, 1),
  ('LIPID',    'TRIG',  'Triglycerides',            '2571-8',  'mg/dL',    NULL, NULL,  150.0, NULL, 1000.0, 2),
  ('LIPID',    'HDL',   'HDL cholesterol',          '2085-9',  'mg/dL',    NULL, 40.0,  NULL,  NULL, NULL, 3),
  ('LIPID',    'LDL',   'LDL cholesterol (calc.)',  '13457-7', 'mg/dL',    NULL, NULL,  130.0, NULL, NULL, 4),
  ('URINE_RE', 'USG',   'Specific gravity',         '5811-5',  '{SG}',     NULL, 1.005, 1.030, NULL, NULL, 1),
  ('URINE_RE', 'UPH',   'pH',                       '5803-2',  '[pH]',     NULL, 4.5,   8.0,   NULL, NULL, 2),
  ('CREAT',    'CREAT', 'Creatinine',               '2160-0',  'mg/dL',    NULL, 0.6,   1.3,   NULL, 10.0, 1),
  ('K',        'K',     'Potassium',                '2823-3',  'mmol/L',   NULL, 3.5,   5.1,   2.5,  6.5, 1),
  ('TROP_I',   'TNI',   'Troponin I',               '10839-9', 'ng/mL',    NULL, NULL,  0.04,  NULL, 0.4, 1),
  ('TSH',      'TSH',   'TSH',                      '3016-3',  'm[IU]/L',  NULL, 0.4,   4.0,   NULL, NULL, 1)
) AS r(test_code, analyte_code, analyte_name, loinc_code, ucum_unit, sex, ref_low, ref_high, critical_low, critical_high, sort_order)
JOIN lab_test t ON t.code = r.test_code
ON CONFLICT DO NOTHING;

INSERT INTO lab_package (id, hospital_id, name, price, active) VALUES
  ('7a000000-0000-4000-8000-000000000001', NULL, 'Basic health check', 1400.00, TRUE),
  ('7a000000-0000-4000-8000-000000000002', NULL, 'Diabetes check',      950.00, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lab_package_item (package_id, test_id)
SELECT p.package_id::uuid, t.id
FROM (VALUES
  ('7a000000-0000-4000-8000-000000000001', 'CBC'),
  ('7a000000-0000-4000-8000-000000000001', 'FBS'),
  ('7a000000-0000-4000-8000-000000000001', 'LIPID'),
  ('7a000000-0000-4000-8000-000000000001', 'URINE_RE'),
  ('7a000000-0000-4000-8000-000000000002', 'FBS'),
  ('7a000000-0000-4000-8000-000000000002', 'HBA1C')
) AS p(package_id, test_code)
JOIN lab_test t ON t.code = p.test_code
ON CONFLICT DO NOTHING;
