-- T08 — ILLUSTRATIVE drug dictionary seed (NOT the DGDA register).
--
-- These rows only demonstrate brand-first mapping (brand as written → product → generic → ATC /
-- RxNorm). Brand names are common Bangladeshi trade names, but strengths/forms are examples, no
-- DGDA registration numbers are given (dgda_reg_no is NULL), and RxNorm codes are indicative.
-- Real data MUST come from the current DGDA drug register via the admin CSV import
-- (/pharmacy/dictionary). Every row seeded here is tagged in drug_data_provenance with
-- source = 'ILLUSTRATIVE_SEED', and the export / UI show it as illustrative.

INSERT INTO drug_generic (name, atc_code, rxnorm_code) VALUES
    ('Paracetamol',        'N02BE01', '161'),
    ('Omeprazole',         'A02BC01', '7646'),
    ('Esomeprazole',       'A02BC05', '283742'),
    ('Pantoprazole',       'A02BC02', '40790'),
    ('Rabeprazole',        'A02BC04', '114979'),
    ('Ciprofloxacin',      'J01MA02', '2551'),
    ('Azithromycin',       'J01FA10', '18631'),
    ('Amoxicillin',        'J01CA04', '723'),
    ('Cefixime',           'J01DD08', '25033'),
    ('Cefuroxime',         'J01DC02', '2194'),
    ('Metronidazole',      'P01AB01', '6922'),
    ('Fexofenadine',       'R06AX26', '87636'),
    ('Cetirizine',         'R06AE07', '20610'),
    ('Montelukast',        'R03DC03', '88249'),
    ('Salbutamol',         'R03AC02', '435'),
    ('Losartan',           'C09CA01', '52175'),
    ('Amlodipine',         'C08CA01', '17767'),
    ('Atorvastatin',       'C10AA05', '83367'),
    ('Rosuvastatin',       'C10AA07', '301542'),
    ('Metformin',          'A10BA02', '6809'),
    ('Gliclazide',         'A10BB09', NULL),
    ('Aspirin',            'B01AC06', '1191'),
    ('Clopidogrel',        'B01AC04', '32968'),
    ('Warfarin',           'B01AA03', '11289'),
    ('Ibuprofen',          'M01AE01', '5640'),
    ('Naproxen',           'M01AE02', '7258'),
    ('Diclofenac',         'M01AB05', '3355'),
    ('Ketorolac',          'M01AB15', '35827'),
    ('Domperidone',        'A03FA03', NULL),
    ('Ondansetron',        'A04AA01', '26225'),
    ('Prednisolone',       'H02AB06', '8638'),
    ('Levothyroxine',      'H03AA01', '10582'),
    ('Calcium carbonate',  'A12AA04', '1897'),
    ('Cholecalciferol',    'A11CC05', '2418'),
    ('Furosemide',         'C03CA01', '4603'),
    ('Spironolactone',     'C03DA01', '9997'),
    ('Fluconazole',        'J02AC01', '4450'),
    ('Albendazole',        'P02CA03', '430'),
    ('Clonazepam',         'N03AE01', '2598')
ON CONFLICT (name) DO NOTHING;

INSERT INTO drug_brand (name, strength, form, generic_id)
SELECT v.brand, v.strength, v.form, g.id
FROM (VALUES
    -- PDF v1.3 §10 example rows
    ('Napa',       '500 mg',        'tablet',     'Paracetamol'),
    ('Seclo',      '20 mg',         'capsule',    'Omeprazole'),
    ('Ciprocin',   '500 mg',        'tablet',     'Ciprofloxacin'),
    -- further illustrative rows
    ('Napa',       '120 mg/5 ml',   'suspension', 'Paracetamol'),
    ('Ace',        '500 mg',        'tablet',     'Paracetamol'),
    ('Fast',       '500 mg',        'tablet',     'Paracetamol'),
    ('Losectil',   '20 mg',         'capsule',    'Omeprazole'),
    ('Sergel',     '20 mg',         'capsule',    'Esomeprazole'),
    ('Nexum',      '20 mg',         'tablet',     'Esomeprazole'),
    ('Maxpro',     '20 mg',         'tablet',     'Esomeprazole'),
    ('Pantonix',   '20 mg',         'tablet',     'Pantoprazole'),
    ('Finix',      '20 mg',         'tablet',     'Rabeprazole'),
    ('Cipro-A',    '500 mg',        'tablet',     'Ciprofloxacin'),
    ('Neofloxin',  '500 mg',        'tablet',     'Ciprofloxacin'),
    ('Zimax',      '500 mg',        'tablet',     'Azithromycin'),
    ('Azithrocin', '500 mg',        'tablet',     'Azithromycin'),
    ('Tridosil',   '500 mg',        'tablet',     'Azithromycin'),
    ('Moxacil',    '500 mg',        'capsule',    'Amoxicillin'),
    ('Tycil',      '500 mg',        'capsule',    'Amoxicillin'),
    ('Cef-3',      '200 mg',        'capsule',    'Cefixime'),
    ('Triocim',    '200 mg',        'capsule',    'Cefixime'),
    ('Cefotil',    '500 mg',        'tablet',     'Cefuroxime'),
    ('Flagyl',     '400 mg',        'tablet',     'Metronidazole'),
    ('Amodis',     '400 mg',        'tablet',     'Metronidazole'),
    ('Fexo',       '120 mg',        'tablet',     'Fexofenadine'),
    ('Telfast',    '120 mg',        'tablet',     'Fexofenadine'),
    ('Alatrol',    '10 mg',         'tablet',     'Cetirizine'),
    ('Monas',      '10 mg',         'tablet',     'Montelukast'),
    ('Montene',    '10 mg',         'tablet',     'Montelukast'),
    ('Sultolin',   '100 mcg/dose',  'inhaler',    'Salbutamol'),
    ('Osartil',    '50 mg',         'tablet',     'Losartan'),
    ('Angilock',   '50 mg',         'tablet',     'Losartan'),
    ('Amdocal',    '5 mg',          'tablet',     'Amlodipine'),
    ('Camlodin',   '5 mg',          'tablet',     'Amlodipine'),
    ('Atova',      '10 mg',         'tablet',     'Atorvastatin'),
    ('Rosuva',     '10 mg',         'tablet',     'Rosuvastatin'),
    ('Comet',      '500 mg',        'tablet',     'Metformin'),
    ('Comprid',    '80 mg',         'tablet',     'Gliclazide'),
    ('Ecosprin',   '75 mg',         'tablet',     'Aspirin'),
    ('Anclog',     '75 mg',         'tablet',     'Clopidogrel'),
    ('Warin',      '5 mg',          'tablet',     'Warfarin'),
    ('Inflam',     '400 mg',        'tablet',     'Ibuprofen'),
    ('Naprosyn',   '500 mg',        'tablet',     'Naproxen'),
    ('Clofenac',   '50 mg',         'tablet',     'Diclofenac'),
    ('Rolac',      '10 mg',         'tablet',     'Ketorolac'),
    ('Omidon',     '10 mg',         'tablet',     'Domperidone'),
    ('Motigut',    '10 mg',         'tablet',     'Domperidone'),
    ('Emistat',    '8 mg',          'tablet',     'Ondansetron'),
    ('Cortan',     '5 mg',          'tablet',     'Prednisolone'),
    ('Thyrox',     '50 mcg',        'tablet',     'Levothyroxine'),
    ('Calbo',      '500 mg',        'tablet',     'Calcium carbonate'),
    ('D-Rise',     '1000 IU',       'capsule',    'Cholecalciferol'),
    ('Lasix',      '40 mg',         'tablet',     'Furosemide'),
    ('Aldactone',  '25 mg',         'tablet',     'Spironolactone'),
    ('Flugal',     '150 mg',        'capsule',    'Fluconazole'),
    ('Alben',      '400 mg',        'tablet',     'Albendazole'),
    ('Rivotril',   '0.5 mg',        'tablet',     'Clonazepam')
) AS v(brand, strength, form, generic)
JOIN drug_generic g ON g.name = v.generic;

INSERT INTO drug_data_provenance (table_name, row_id, source, note)
SELECT 'drug_generic', id, 'ILLUSTRATIVE_SEED', 'Illustrative sample — verify against WHO ATC / RxNorm' FROM drug_generic
ON CONFLICT (table_name, row_id) DO NOTHING;

INSERT INTO drug_data_provenance (table_name, row_id, source, note)
SELECT 'drug_brand', id, 'ILLUSTRATIVE_SEED', 'Illustrative sample — not from the DGDA register' FROM drug_brand
ON CONFLICT (table_name, row_id) DO NOTHING;
