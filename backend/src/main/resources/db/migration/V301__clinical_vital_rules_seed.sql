-- Adult vital definitions + rule-table seed (R&D v1.3 §11). Ranges are half-open [min, max).
-- Adding a new vital (e.g. glucose) = INSERT rows here; no Java change.

INSERT INTO clinical_vital_definition
    (code, kind, ucum_unit, loinc_code, display_en, display_bn, plausible_min, plausible_max, sort_order, nurse_entry)
VALUES
    ('BP',          'BLOOD_PRESSURE', 'mm[Hg]',  '85354-9', 'Blood pressure',            'রক্তচাপ',                NULL, NULL, 10, TRUE),
    ('HR',          'NUMERIC',        '/min',    '8867-4',  'Heart rate',                'হৃদস্পন্দন',             20,   300,  20, TRUE),
    ('RR',          'NUMERIC',        '/min',    '9279-1',  'Respiratory rate',          'শ্বাসের হার',            1,    80,   30, TRUE),
    ('TEMP',        'NUMERIC',        'Cel',     '8310-5',  'Body temperature',          'শরীরের তাপমাত্রা',       25,   45,   40, TRUE),
    ('SPO2',        'NUMERIC',        '%',       '59408-5', 'Oxygen saturation (SpO2)',  'অক্সিজেন স্যাচুরেশন',    50,   100,  50, TRUE),
    ('MAP',         'DERIVED',        'mm[Hg]',  '8478-0',  'Mean arterial pressure',    'গড় ধমনী চাপ',           NULL, NULL, 100, FALSE),
    ('SHOCK_INDEX', 'DERIVED',        '{ratio}', NULL,      'Shock index',               'শক ইনডেক্স',             NULL, NULL, 110, FALSE);

-- vital_type / population unique in V1. Half-open encoding of the published adult table.
INSERT INTO vital_rule
    (vital_type, population, unit, normal_min, normal_max, borderline_min, borderline_max, critical_min, critical_max, active)
VALUES
    ('HR',           'ADULT', '/min',    60,     101,    50,     130,    40,     130,    TRUE),
    ('RR',           'ADULT', '/min',    12,     21,     9,      25,     9,      25,     TRUE),
    ('TEMP',         'ADULT', 'Cel',     36.1,   37.3,   35.1,   39.5,   35.1,   39.5,   TRUE),
    ('SPO2',         'ADULT', '%',       95,     NULL,   91,     95,     91,     NULL,   TRUE),
    ('BP_SYSTOLIC',  'ADULT', 'mm[Hg]',  NULL,   120,    120,    180,    90,     180,    TRUE),
    ('BP_DIASTOLIC', 'ADULT', 'mm[Hg]',  NULL,   80,     80,     120,    NULL,   120,    TRUE),
    ('MAP',          'ADULT', 'mm[Hg]',  65,     NULL,   NULL,   NULL,   65,     NULL,   TRUE),
    ('SHOCK_INDEX',  'ADULT', '{ratio}', NULL,   0.9,    0.9,    1.0,    NULL,   1.0,    TRUE);
