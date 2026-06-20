-- Hibernate 6 génère pin_bloque_jusqua (sans underscore avant a)
-- mais V1 a créé pin_bloque_jusqu_a
ALTER TABLE utilisateurs
    RENAME COLUMN pin_bloque_jusqu_a TO pin_bloque_jusqua;
