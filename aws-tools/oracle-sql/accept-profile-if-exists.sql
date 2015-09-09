set echo off
set verify off
set serveroutput on

-- Accept the SQL Profile
variable profile_name varchar2(100)
begin
 :profile_name := dbms_sqltune.accept_sql_profile(task_name=>:task_name,replace=>true);
 dbms_output.put_line(:profile_name);
end;
/

