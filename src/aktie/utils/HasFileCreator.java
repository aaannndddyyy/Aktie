package aktie.utils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.hibernate.Session;

import aktie.crypto.Utils;
import aktie.data.CObj;
import aktie.data.CommunityMember;
import aktie.data.HH2Session;
import aktie.gui.Wrapper;
import aktie.index.CObjList;
import aktie.index.Index;

public class HasFileCreator
{
    Logger log = Logger.getLogger ( "aktie" );

    private HH2Session session;
    private Index index;
    private SubscriptionValidator validator;

    public HasFileCreator ( HH2Session s, Index i )
    {
        index = i;
        session = s;
        validator = new SubscriptionValidator ( index );
    }

    public void updateDownloadRequested ( CObj hasfile )
    {

    }

    /**
        Called whenever we get a hasfile record
        @param f
    */
    public void updateFileInfo ( CObj f )
    {
        if ( !CObj.HASFILE.equals ( f.getType() ) )
        {
            throw new RuntimeException ( "This should only be called with hasfile." );
        }

        //This makes the hasfile immediately available
        //so that it is counted correctly for updating
        //the file info
        index.forceNewSearcher();

        //Create FILE type CObj.  only index if new
        String comid = f.getString ( CObj.COMMUNITYID );
        Long filesize = f.getNumber ( CObj.FILESIZE );
        Long fragsize = f.getNumber ( CObj.FRAGSIZE );
        Long fragnumber = f.getNumber ( CObj.FRAGNUMBER );
        String digofdigs = f.getString ( CObj.FRAGDIGEST );
        String wholedig = f.getString ( CObj.FILEDIGEST );
        String name = f.getString ( CObj.NAME );
        String localfile = f.getPrivate ( CObj.LOCALFILE );
        String txtname = f.getString ( CObj.TXTNAME );
        String stillhas = f.getString ( CObj.STILLHASFILE );
        String share = f.getString ( CObj.SHARE_NAME );

        if ( txtname == null )
        {
            txtname = name;
        }

        if ( digofdigs != null && wholedig != null && name != null && comid != null && filesize != null &&
                fragsize != null && fragnumber != null )
        {

            CObjList wl = index.getHasFiles ( comid, wholedig, digofdigs );
            int numberhasfile = wl.size();
            wl.close();

            String id = Utils.mergeIds ( comid, digofdigs, wholedig );
            CObj fi = index.getFileInfo ( id );

            if ( fi == null )
            {
                fi = new CObj();
                fi.setId ( id );
                fi.setType ( CObj.FILE );
            }

            fi.pushNumber ( CObj.NUMBER_HAS, numberhasfile );
            String creatorid = f.getString ( CObj.CREATOR );

            if ( creatorid != null )
            {
                CObj creator = index.getIdentity ( creatorid );

                if ( creator != null )
                {
                    Long rnk = creator.getPrivateNumber ( CObj.PRV_USER_RANK );

                    if ( rnk != null )
                    {
                        Long crnk = fi.getPrivateNumber ( CObj.PRV_USER_RANK );

                        if ( crnk == null || crnk < rnk )
                        {
                            fi.pushPrivateNumber ( CObj.PRV_USER_RANK, rnk );
                        }

                    }

                }

            }

            try
            {
                if ( localfile != null )
                {
                    if ( "false".equals ( stillhas ) )
                    {
                        fi.pushString ( CObj.STATUS, "" );
                        localfile = "";
                    }

                    else
                    {
                        fi.pushString ( CObj.STATUS, "done" );
                    }

                    fi.pushString ( CObj.LOCALFILE, localfile );
                    fi.pushString ( CObj.SHARE_NAME, share );
                }

                fi.pushString ( CObj.COMMUNITYID, comid );
                fi.pushString ( CObj.FILEDIGEST, wholedig );
                fi.pushString ( CObj.FRAGDIGEST, digofdigs );
                fi.pushNumber ( CObj.FILESIZE, filesize );
                fi.pushNumber ( CObj.FRAGSIZE, fragsize );
                fi.pushNumber ( CObj.FRAGNUMBER, fragnumber );
                fi.pushString ( CObj.NAME, name );
                fi.pushText ( CObj.TXTNAME, txtname );

                index.index ( fi );

            }

            catch ( Exception e )
            {
                e.printStackTrace();
            }


        }

    }

    public static String getCommunityMemberId ( String creator, String comid )
    {
        return Utils.mergeIds ( creator, comid );
    }

    public static String getHasFileId ( String commemid, String digofdigs, String wholedig )
    {
        String hasfileid = Utils.mergeIds ( commemid, digofdigs, wholedig );
        return hasfileid;
    }

    public static String getHasFileId ( String creator, String comid, String digofdigs, String wholedig )
    {
        String id = getCommunityMemberId ( creator, comid );
        return getHasFileId ( id, digofdigs, wholedig );
    }

    public void updateHasFile()
    {
        CObjList hflst = index.getAllHasFiles();

        for ( int c = 0; c < hflst.size(); c++ )
        {
            try
            {
                CObj b = hflst.get ( c );

                String creatorid = b.getString ( CObj.CREATOR );
                String comid = b.getString ( CObj.COMMUNITYID );
                String wdig = b.getString ( CObj.FILEDIGEST );
                String ddig = b.getString ( CObj.FRAGDIGEST );

                if ( creatorid != null && comid != null &&
                        wdig != null && ddig != null )
                {

                    String id = HasFileCreator.getCommunityMemberId ( creatorid, comid );

                    //Hasfileid is an upgrade.  We just set it here to what it is supposed to
                    //be.  If the signature does not match with the id value set.  DigestValidator
                    //has been upgraded to check the signature with a null id value for
                    //hasfile records. All new hasfile records should have the proper id value
                    //set so this does nothing.
                    String hasfileid = HasFileCreator.getHasFileId ( id, ddig, wdig );

                    index.delete ( b );
                    b.setId ( hasfileid );
                    index.index ( b );
                    updateFileInfo ( b );
                }

            }

            catch ( Exception e )
            {
                e.printStackTrace();
            }

        }

        hflst.close();

    }

    public boolean createHasFile ( CObj o )
    {
        //Set File sequence number for the community/creator
        String creator = o.getString ( CObj.CREATOR );
        String comid = o.getString ( CObj.COMMUNITYID );

        String digofdigs = o.getString ( CObj.FRAGDIGEST );
        String wholedig = o.getString ( CObj.FILEDIGEST );

        CObj myid = validator.isMyUserSubscribed ( comid, creator );

        if ( myid == null ) { return false; }

        String id = getCommunityMemberId ( creator, comid );
        String hasfileid = getHasFileId ( id, digofdigs, wholedig );

        o.setId ( hasfileid ); //only 1 has file per user per community per file digest
        //This is an upgrade.  We have to make adjustments for this
        //to be null when validating signatures for old hasfile records

        Session s = null;
        long lastnum = 0;

        try
        {
            s = session.getSession();
            s.getTransaction().begin();
            CommunityMember m = ( CommunityMember ) s.get ( CommunityMember.class, id );

            if ( m == null )
            {
                m = new CommunityMember();
                m.setId ( id );
                m.setCommunityId ( comid );
                m.setMemberId ( creator );
                s.persist ( m );
            }

            long num = m.getLastFileNumber();
            lastnum = num;
            num++;
            o.pushNumber ( CObj.SEQNUM, num );
            o.pushPrivate ( CObj.MINE, "true" );
            m.setLastFileNumber ( num );
            s.merge ( m );
            s.getTransaction().commit();
            s.close();
        }

        catch ( Exception e )
        {
            e.printStackTrace();
            o.pushString ( CObj.ERROR, "Bad error: " + e.getMessage() );
            e.printStackTrace();

            if ( s != null )
            {
                try
                {
                    if ( s.getTransaction().isActive() )
                    {
                        s.getTransaction().rollback();
                    }

                }

                catch ( Exception e2 )
                {
                }

                try
                {
                    s.close();
                }

                catch ( Exception e2 )
                {
                }

            }

            return false;
        }

        //Make the path absolute to help with queries based on the file
        //name later.
        String lf = o.getPrivate ( CObj.LOCALFILE );

        if ( lf != null )
        {
            File f = new File ( lf );

            if ( f.exists() )
            {
                try
                {
                    lf = f.getCanonicalPath();
                    o.pushPrivate ( CObj.LOCALFILE, lf );
                }

                catch ( IOException e )
                {
                    e.printStackTrace();
                }

            }

        }

        //If the file is a duplicate remove it
        CObj ed = index.getDuplicate ( hasfileid, lf );

        if ( ed != null )
        {
            try
            {
                index.delete ( ed );
            }

            catch ( Exception e )
            {
                e.printStackTrace();
            }

        }

        //See if there is an existing file already for it
        //Make the old one a duplicate
        CObj oldfile = index.getById ( hasfileid );

        if ( oldfile != null )
        {
            String oldlocal = oldfile.getPrivate ( CObj.LOCALFILE );

            if ( oldlocal != null && !oldlocal.equals ( lf ) )
            {
                File f = new File ( oldlocal );

                if ( f.exists() )
                {
                    CObj dp = new CObj();
                    dp.setType ( CObj.DUPFILE );
                    dp.pushString ( CObj.HASFILE, hasfileid );
                    dp.pushString ( CObj.LOCALFILE, oldlocal );
                    dp.pushString ( CObj.COMMUNITYID, comid );
                    dp.pushString ( CObj.CREATOR, creator );
                    dp.simpleDigest();

                    try
                    {
                        index.index ( dp );
                        index.forceNewSearcher();
                    }

                    catch ( Exception e )
                    {
                        e.printStackTrace();
                    }

                }

            }

        }

        //Get the time of the last file and make sure our new fuzzy time
        //is after that.
        long lasttime = 0;
        CObjList ol = index.getHasFiles ( comid, creator, lastnum, lastnum );

        if ( ol.size() > 0 )
        {
            try
            {
                CObj of = ol.get ( 0 );

                if ( of != null )
                {
                    Long ct = of.getNumber ( CObj.CREATEDON );

                    if ( ct != null )
                    {
                        lasttime = ct;
                    }

                }

            }

            catch ( IOException e )
            {
                e.printStackTrace();
            }

        }

        ol.close();

        //Set the rank of the post based on the rank of the
        //user
        Long rnk = myid.getPrivateNumber ( CObj.PRV_USER_RANK );

        if ( rnk != null )
        {
            o.pushPrivateNumber ( CObj.PRV_USER_RANK, rnk );
        }

        //Set the created on time
        o.pushNumber ( CObj.CREATEDON, Utils.fuzzTime ( lasttime + 1 ) );

        //Determine if this is a private or public community
        CObj com = index.getCommunity ( comid );
        int payment = 0;

        if ( com != null && CObj.SCOPE_PUBLIC.equals ( com.getString ( CObj.SCOPE ) ) )
        {
            payment = Wrapper.getGenPayment();
        }

        //Sign it.
        o.sign ( Utils.privateKeyFromString ( myid.getPrivate ( CObj.PRIVATEKEY ) ),
                 payment );

        try
        {
            index.index ( o );
        }

        catch ( Exception e )
        {
            e.printStackTrace();
            o.pushString ( CObj.ERROR, "File record could not be indexed" );
            return false;
        }

        return true;
    }

}
