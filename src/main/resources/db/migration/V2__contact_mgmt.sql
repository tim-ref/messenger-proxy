ALTER TABLE contacts
    ADD CONSTRAINT unique_contacts UNIQUE(owner_id, approved_id);
