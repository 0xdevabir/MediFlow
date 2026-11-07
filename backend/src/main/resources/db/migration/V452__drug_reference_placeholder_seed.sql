-- T08 — PLACEHOLDER interaction and dose-limit data.
--
-- PLACEHOLDER — NEEDS LICENSED SOURCE. This small curated set exists only so that the T06
-- contradiction engine has something to run against in development and tests. It is NOT a
-- clinical reference and must be replaced by a licensed, versioned drug database before any
-- pilot. Every row is tagged in drug_data_provenance with source = 'PLACEHOLDER_SEED', and every
-- interaction description starts with "[PLACEHOLDER — needs licensed source]".
--
-- drug_dose_limit conventions (also documented in docs/plan/reports/T08.md):
--   population : ADULT | PAEDIATRIC
--   route      : ORAL (others may be added)
--   unit       : <mass>/dose | <mass>/day | <mass>/kg/dose | <mass>/kg/day, mass ∈ mg | mcg
--   min_dose / max_dose : inclusive bounds in that unit (either may be NULL = no bound)

INSERT INTO drug_interaction (generic_a_id, generic_b_id, severity, description)
SELECT a.id, b.id, v.severity, '[PLACEHOLDER — needs licensed source] ' || v.description
FROM (VALUES
    ('Warfarin',      'Aspirin',           'CRITICAL', 'Additive bleeding risk'),
    ('Warfarin',      'Ibuprofen',         'CRITICAL', 'NSAID with anticoagulant: bleeding risk'),
    ('Warfarin',      'Naproxen',          'CRITICAL', 'NSAID with anticoagulant: bleeding risk'),
    ('Warfarin',      'Diclofenac',        'CRITICAL', 'NSAID with anticoagulant: bleeding risk'),
    ('Warfarin',      'Metronidazole',     'CRITICAL', 'Raises INR markedly'),
    ('Warfarin',      'Ciprofloxacin',     'WARNING',  'May raise INR; monitor'),
    ('Warfarin',      'Fluconazole',       'CRITICAL', 'Raises INR markedly'),
    ('Clopidogrel',   'Omeprazole',        'WARNING',  'May reduce antiplatelet effect'),
    ('Clopidogrel',   'Esomeprazole',      'WARNING',  'May reduce antiplatelet effect'),
    ('Aspirin',       'Ibuprofen',         'WARNING',  'May reduce cardioprotective effect; GI bleeding risk'),
    ('Ketorolac',     'Ibuprofen',         'CRITICAL', 'Two NSAIDs: GI bleeding / renal risk'),
    ('Ciprofloxacin', 'Calcium carbonate', 'WARNING',  'Reduced ciprofloxacin absorption; separate doses'),
    ('Levothyroxine', 'Calcium carbonate', 'WARNING',  'Reduced levothyroxine absorption; separate doses'),
    ('Losartan',      'Spironolactone',    'WARNING',  'Hyperkalaemia risk'),
    ('Azithromycin',  'Ondansetron',       'WARNING',  'Additive QT prolongation'),
    ('Domperidone',   'Azithromycin',      'WARNING',  'Additive QT prolongation')
) AS v(a, b, severity, description)
JOIN drug_generic a ON a.name = v.a
JOIN drug_generic b ON b.name = v.b;

INSERT INTO drug_dose_limit (generic_id, population, route, min_dose, max_dose, unit)
SELECT g.id, v.population, v.route, v.min_dose, v.max_dose, v.unit
FROM (VALUES
    ('Paracetamol',   'ADULT',      'ORAL', 500::numeric, 1000::numeric, 'mg/dose'),
    ('Paracetamol',   'ADULT',      'ORAL', NULL,         4000,          'mg/day'),
    ('Paracetamol',   'PAEDIATRIC', 'ORAL', 10,           15,            'mg/kg/dose'),
    ('Paracetamol',   'PAEDIATRIC', 'ORAL', NULL,         60,            'mg/kg/day'),
    ('Ibuprofen',     'ADULT',      'ORAL', 200,          800,           'mg/dose'),
    ('Ibuprofen',     'ADULT',      'ORAL', NULL,         2400,          'mg/day'),
    ('Ibuprofen',     'PAEDIATRIC', 'ORAL', 5,            10,            'mg/kg/dose'),
    ('Ibuprofen',     'PAEDIATRIC', 'ORAL', NULL,         40,            'mg/kg/day'),
    ('Amoxicillin',   'ADULT',      'ORAL', 250,          1000,          'mg/dose'),
    ('Amoxicillin',   'PAEDIATRIC', 'ORAL', NULL,         90,            'mg/kg/day'),
    ('Azithromycin',  'ADULT',      'ORAL', 250,          500,           'mg/dose'),
    ('Azithromycin',  'PAEDIATRIC', 'ORAL', 5,            10,            'mg/kg/day'),
    ('Ciprofloxacin', 'ADULT',      'ORAL', 250,          750,           'mg/dose'),
    ('Ciprofloxacin', 'ADULT',      'ORAL', NULL,         1500,          'mg/day'),
    ('Metformin',     'ADULT',      'ORAL', 500,          1000,          'mg/dose'),
    ('Metformin',     'ADULT',      'ORAL', NULL,         2000,          'mg/day'),
    ('Omeprazole',    'ADULT',      'ORAL', 10,           40,            'mg/dose'),
    ('Amlodipine',    'ADULT',      'ORAL', 2.5,          10,            'mg/day'),
    ('Warfarin',      'ADULT',      'ORAL', NULL,         10,            'mg/day'),
    ('Metronidazole', 'ADULT',      'ORAL', 200,          500,           'mg/dose'),
    ('Metronidazole', 'PAEDIATRIC', 'ORAL', NULL,         30,            'mg/kg/day'),
    ('Ketorolac',     'ADULT',      'ORAL', NULL,         40,            'mg/day'),
    ('Clonazepam',    'ADULT',      'ORAL', NULL,         20,            'mg/day')
) AS v(generic, population, route, min_dose, max_dose, unit)
JOIN drug_generic g ON g.name = v.generic;

INSERT INTO drug_data_provenance (table_name, row_id, source, note)
SELECT 'drug_interaction', id, 'PLACEHOLDER_SEED', 'Placeholder — needs licensed source' FROM drug_interaction
ON CONFLICT (table_name, row_id) DO NOTHING;

INSERT INTO drug_data_provenance (table_name, row_id, source, note)
SELECT 'drug_dose_limit', id, 'PLACEHOLDER_SEED', 'Placeholder — needs licensed source' FROM drug_dose_limit
ON CONFLICT (table_name, row_id) DO NOTHING;
