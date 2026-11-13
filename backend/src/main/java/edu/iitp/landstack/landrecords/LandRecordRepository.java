package edu.iitp.landstack.landrecords;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LandRecordRepository extends JpaRepository<LandRecord, UUID>, JpaSpecificationExecutor<LandRecord> {

    long countByImportJobId(UUID importJobId);

    List<LandRecord> findByImportJobId(UUID importJobId);

    @Query("""
           select r from LandRecord r
           where r.recordStatus = edu.iitp.landstack.landrecords.LandRecord.Status.CONFIRMED
             and r.id <> :selfId and r.importJobId <> :jobId
             and lower(r.parcelId) = :parcel
             and lower(coalesce(r.villageLocality, '')) = :village
             and lower(coalesce(r.district, '')) = :district
           """)
    List<LandRecord> parcelDuplicates(@Param("parcel") String parcelLower, @Param("village") String villageLower,
                                      @Param("district") String districtLower, @Param("selfId") UUID selfId,
                                      @Param("jobId") UUID jobId, Pageable limit);

    @Query("""
           select r from LandRecord r
           where r.recordStatus = edu.iitp.landstack.landrecords.LandRecord.Status.CONFIRMED
             and r.id <> :selfId and r.importJobId <> :jobId
             and lower(r.surveyKhasraNo) = :khasra
             and lower(coalesce(r.villageLocality, '')) = :village
             and lower(coalesce(r.district, '')) = :district
           """)
    List<LandRecord> khasraDuplicates(@Param("khasra") String khasraLower, @Param("village") String villageLower,
                                      @Param("district") String districtLower, @Param("selfId") UUID selfId,
                                      @Param("jobId") UUID jobId, Pageable limit);

    @Modifying
    @Query("delete from LandRecord r where r.importJobId = :jobId")
    int deleteByImportJobId(@Param("jobId") UUID jobId);

    @Query("select distinct r.district from LandRecord r where r.district is not null order by r.district")
    List<String> distinctDistricts(Pageable limit);

    @Query("select distinct r.state from LandRecord r where r.state is not null order by r.state")
    List<String> distinctStates(Pageable limit);

    @Query("select distinct r.landTypeCategory from LandRecord r where r.landTypeCategory is not null order by r.landTypeCategory")
    List<String> distinctCategories(Pageable limit);
}
