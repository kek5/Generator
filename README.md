# Generator
pstgrsql statement creator

Article:
  fields:
      title: varchar(50)
      text: text
  relations:
      Category: one
      Tag: many

Category:
  fields:
      title: varchar(50)
  relations:
      Article: many

Tag:
  fields:
      value: varchar(50)
  relations:
      Article: many
      
      
create tables with correspondent column names-types 
+ adding relations
      
