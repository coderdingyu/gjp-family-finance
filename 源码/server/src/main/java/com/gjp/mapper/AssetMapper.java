package com.gjp.mapper;

import com.gjp.entity.Asset;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AssetMapper {

    @Select("SELECT * FROM t_asset WHERE family_id = #{familyId} ORDER BY asset_type, id")
    List<Asset> selectByFamily(@Param("familyId") Long familyId);

    @Select("SELECT * FROM t_asset WHERE id = #{id} AND family_id = #{familyId}")
    Asset selectById(@Param("id") Long id, @Param("familyId") Long familyId);

    @Insert("INSERT INTO t_asset (family_id, asset_name, asset_type, amount, cost, buy_date, remark) "
            + "VALUES (#{familyId}, #{assetName}, #{assetType}, #{amount}, #{cost}, #{buyDate}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Asset asset);

    @Update("UPDATE t_asset SET asset_name = #{assetName}, asset_type = #{assetType}, amount = #{amount}, "
            + "cost = #{cost}, buy_date = #{buyDate}, remark = #{remark} "
            + "WHERE id = #{id} AND family_id = #{familyId}")
    int update(Asset asset);

    @Delete("DELETE FROM t_asset WHERE id = #{id} AND family_id = #{familyId}")
    int deleteById(@Param("id") Long id, @Param("familyId") Long familyId);
}
