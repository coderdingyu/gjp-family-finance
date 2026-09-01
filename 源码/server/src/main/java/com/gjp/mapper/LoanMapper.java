package com.gjp.mapper;

import com.gjp.entity.Loan;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface LoanMapper {

    @Select("SELECT * FROM t_loan WHERE family_id = #{familyId} ORDER BY id")
    List<Loan> selectByFamily(@Param("familyId") Long familyId);

    @Select("SELECT * FROM t_loan WHERE id = #{id} AND family_id = #{familyId}")
    Loan selectById(@Param("id") Long id, @Param("familyId") Long familyId);

    @Insert("INSERT INTO t_loan (family_id, loan_name, loan_type, total_amount, monthly_payment, "
            + "total_months, paid_months, start_date) "
            + "VALUES (#{familyId}, #{loanName}, #{loanType}, #{totalAmount}, #{monthlyPayment}, "
            + "#{totalMonths}, #{paidMonths}, #{startDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Loan loan);

    @Update("UPDATE t_loan SET loan_name = #{loanName}, loan_type = #{loanType}, "
            + "total_amount = #{totalAmount}, monthly_payment = #{monthlyPayment}, "
            + "total_months = #{totalMonths}, paid_months = #{paidMonths}, start_date = #{startDate} "
            + "WHERE id = #{id} AND family_id = #{familyId}")
    int update(Loan loan);

    @Delete("DELETE FROM t_loan WHERE id = #{id} AND family_id = #{familyId}")
    int deleteById(@Param("id") Long id, @Param("familyId") Long familyId);
}
