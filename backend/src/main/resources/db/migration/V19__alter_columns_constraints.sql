ALTER TABLE learning_material
ALTER COLUMN title TYPE varchar(100),
ALTER COLUMN description TYPE varchar(1000);

ALTER TABLE training_program
ALTER COLUMN title TYPE varchar(100),
ALTER COLUMN description TYPE varchar(1000);

ALTER TABLE learning_material
ALTER COLUMN material_type TYPE TEXT[]
USING ARRAY[material_type]::TEXT[];

